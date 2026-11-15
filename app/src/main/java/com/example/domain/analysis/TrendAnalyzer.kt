package com.example.domain.analysis

import com.example.data.model.AggregationMode
import com.example.data.model.AnalysisStatus
import com.example.data.model.ComparisonCorrelation
import com.example.data.model.DataProvenanceStatus
import com.example.data.model.DatasetMetadata
import com.example.data.model.InvestigationConfig
import com.example.data.model.QualityStatus
import com.example.data.model.SlopeMethod
import com.example.data.model.StudyRegion
import com.example.data.model.TimeSeriesPoint
import com.example.data.model.TrendDirection
import com.example.data.model.TrendLineEndpoint
import com.example.data.model.TrendMethod
import com.example.data.model.VariableDefinition
import com.example.data.model.VariableTrendResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Deterministic scientific trend analysis engine (Spec Sections 11 & 17).
 * - Never replaces missing/flagged values with zero.
 * - Supports Monthly, Deseasonalized Anomaly, and Annual Mean aggregations.
 * - Computes Theil-Sen robust slope (+ 95% CI), OLS linear slope/R2,
 *   Standard Mann-Kendall, Seasonal Mann-Kendall, and Lag-1 autocorrelation checks.
 */
object TrendAnalyzer {

    const val PROCESSING_VERSION = "earthpulse-stats-1.0.0"
    private const val MIN_VALID_OBSERVATIONS = 12
    private const val MAX_ALLOWED_MISSING_FRACTION = 0.45

    fun analyze(
        rawPoints: List<TimeSeriesPoint>,
        variable: VariableDefinition,
        dataset: DatasetMetadata,
        region: StudyRegion,
        config: InvestigationConfig,
        provenanceStatus: DataProvenanceStatus,
        retrievedAtIso: String,
    ): VariableTrendResult {
        // 1. Filter to requested year window & enforce strict quality control (never convert missing -> 0)
        val filteredRaw = rawPoints
            .filter { it.year in config.startYear..config.endYear }
            .sortedBy { it.decimalYear }
            .map { pt ->
                val rawVal = pt.rawValue
                val qcStatus = when {
                    pt.qualityStatus != QualityStatus.VALID -> pt.qualityStatus
                    rawVal == null || !rawVal.isFinite() || rawVal <= -900.0 -> QualityStatus.MISSING
                    rawVal < variable.validMin || rawVal > variable.validMax -> QualityStatus.OUT_OF_RANGE
                    else -> QualityStatus.VALID
                }
                pt.copy(
                    value = if (qcStatus == QualityStatus.VALID) rawVal else null,
                    qualityStatus = qcStatus,
                )
            }

        if (filteredRaw.isEmpty()) {
            return buildInsufficientResult(
                variable = variable,
                dataset = dataset,
                region = region,
                config = config,
                provenanceStatus = provenanceStatus,
                retrievedAtIso = retrievedAtIso,
                series = emptyList(),
                status = AnalysisStatus.NO_DATA,
                reason = "No NASA observations were returned for ${region.name} (${config.startYear}–${config.endYear}). Try expanding the period or selecting another study region.",
            )
        }

        // 2. Apply documented temporal aggregation while preserving missing slots
        val aggregatedSeries = applyAggregation(filteredRaw, config.aggregation)
        val nTotalSlots = aggregatedSeries.size
        val validPoints = aggregatedSeries.filter { it.value != null && it.qualityStatus == QualityStatus.VALID }
        val nValid = validPoints.size
        val nMissing = nTotalSlots - nValid
        val missingFraction = if (nTotalSlots > 0) nMissing.toDouble() / nTotalSlots.toDouble() else 1.0

        val periodStart = aggregatedSeries.first().yearMonth
        val periodEnd = aggregatedSeries.last().yearMonth
        val lastValidDate = validPoints.lastOrNull()?.yearMonth ?: "Unavailable"

        // 3. Guard against short or overly incomplete series (Spec Section 05 Recovery Path)
        val effectiveMinObs = if (config.aggregation == AggregationMode.ANNUAL_MEAN) 6 else MIN_VALID_OBSERVATIONS
        if (nValid < effectiveMinObs || missingFraction > MAX_ALLOWED_MISSING_FRACTION) {
            val pctMissing = String.format(Locale.US, "%.1f%%", missingFraction * 100.0)
            return buildInsufficientResult(
                variable = variable,
                dataset = dataset,
                region = region,
                config = config,
                provenanceStatus = provenanceStatus,
                retrievedAtIso = retrievedAtIso,
                series = aggregatedSeries,
                status = AnalysisStatus.INSUFFICIENT_DATA,
                reason = "Insufficient valid observations ($nValid valid slots, $pctMissing missing/cloud-flagged). A minimum of $effectiveMinObs valid slots and <= 45% missingness is required to avoid a misleading trend.",
            )
        }

        // 4. Deterministic slope estimation (Theil-Sen and OLS)
        val ols = computeOls(validPoints)
        val sen = computeSenSlopeAndCi(validPoints)

        val chosenSlopePerYear = when (config.slopeMethod) {
            SlopeMethod.SENS_SLOPE -> sen.slopePerYear
            SlopeMethod.OLS_LINEAR -> ols.slopePerYear
        }
        val chosenSlopePerDecade = chosenSlopePerYear * 10.0

        // Compute intercept for visual trend line centered around Sen or OLS
        val trendLine = if (config.slopeMethod == SlopeMethod.SENS_SLOPE) {
            val medianT = median(validPoints.map { it.decimalYear })
            val medianY = median(validPoints.map { it.value!! })
            val intercept = medianY - sen.slopePerYear * medianT
            val t0 = aggregatedSeries.first().decimalYear
            val t1 = aggregatedSeries.last().decimalYear
            TrendLineEndpoint(
                startYear = t0,
                startValue = intercept + sen.slopePerYear * t0,
                endYear = t1,
                endValue = intercept + sen.slopePerYear * t1,
            )
        } else {
            val t0 = aggregatedSeries.first().decimalYear
            val t1 = aggregatedSeries.last().decimalYear
            TrendLineEndpoint(
                startYear = t0,
                startValue = ols.intercept + ols.slopePerYear * t0,
                endYear = t1,
                endValue = ols.intercept + ols.slopePerYear * t1,
            )
        }

        // 5. Mann-Kendall or Seasonal Mann-Kendall significance test
        val useSeasonalMk = config.trendMethod == TrendMethod.SEASONAL_MANN_KENDALL &&
            config.aggregation != AggregationMode.ANNUAL_MEAN

        val mkResult = if (useSeasonalMk) {
            computeSeasonalMannKendall(validPoints)
        } else {
            computeStandardMannKendall(validPoints)
        }

        val isSignificant = mkResult.pValue < config.significanceAlpha

        // 6. Lag-1 autocorrelation diagnostic on detrended residuals
        val lag1 = computeLag1Autocorrelation(aggregatedSeries, chosenSlopePerYear, trendLine)
        val hasAutocorrCaveat = lag1 != null && abs(lag1) > 0.25

        // 7. Direction & plain-language scientific interpretation (Spec Section 11)
        val direction = when {
            !isSignificant && abs(chosenSlopePerDecade) < 1e-6 -> TrendDirection.NO_CLEAR_DIRECTION
            !isSignificant -> {
                // Report estimated direction if non-trivial, or no clear direction if near zero
                if (abs(mkResult.zScore) < 0.5) TrendDirection.NO_CLEAR_DIRECTION
                else if (chosenSlopePerDecade > 0) TrendDirection.INCREASING
                else TrendDirection.DECREASING
            }
            chosenSlopePerDecade > 0 -> TrendDirection.INCREASING
            chosenSlopePerDecade < 0 -> TrendDirection.DECREASING
            else -> TrendDirection.NO_CLEAR_DIRECTION
        }

        val directionPhrase = when (direction) {
            TrendDirection.INCREASING -> "an upward"
            TrendDirection.DECREASING -> "a downward"
            TrendDirection.NO_CLEAR_DIRECTION -> "no clear directional"
        }

        val sigSentence = if (isSignificant) {
            "The estimated trend is statistically significant under the selected test and assumptions."
        } else {
            "The analysis does not provide sufficient evidence of a statistically significant monotonic trend over this period."
        }

        val unitLabel = if (config.aggregation == AggregationMode.DESEASONALIZED_ANOMALY) {
            "${variable.unit} anomaly"
        } else {
            variable.unit
        }

        val formattedSlope = String.format(Locale.US, "%+.3f %s", chosenSlopePerDecade, variable.decadeSlopeUnit)
        val formattedP = formatPValue(mkResult.pValue)
        val ciText = if (sen.ciLowPerDecade != null && sen.ciHighPerDecade != null) {
            String.format(Locale.US, "[%+.3f, %+.3f] %s", sen.ciLowPerDecade, sen.ciHighPerDecade, variable.decadeSlopeUnit)
        } else {
            "Not computed"
        }

        val neutralTitle = "${config.aggregation.label} ${variable.displayName.lowercase(Locale.US)} shows $directionPhrase tendency in ${region.name}"

        val whatThisMeans = buildString {
            append("Over ${config.startYear}–${config.endYear} ($nValid valid observations), ")
            append("${variable.displayName} in ${region.name} shows an estimated rate of $formattedSlope ")
            append("(95% CI: $ciText, p = $formattedP vs α = ${config.significanceAlpha}). ")
            append(sigSentence)
        }

        val whatThisDoesNotMean = buildString {
            append("This finding describes regional historical tendency at ${dataset.spatialResolution} resolution—not a local weather forecast, emergency alert, or proof of a single causal driver. ")
            if (!isSignificant) {
                append("Lack of statistical significance does not mean zero physical change occurred; high year-to-year variability or sample length may mask a modest shift.")
            } else {
                append("Statistical significance depends on test assumptions (including independence and monotonic behavior) and does not automatically quantify ecological or societal impact.")
            }
        }

        val limitations = buildList {
            add(dataset.limitations)
            add(variable.scientificCaveat)
            if (nMissing > 0) {
                val pct = String.format(Locale.US, "%.1f%%", missingFraction * 100.0)
                add("$nMissing of $nTotalSlots slots ($pct) were missing or quality-flagged and preserved as gaps rather than filled with zero.")
            }
            if (hasAutocorrCaveat && lag1 != null) {
                add(
                    String.format(
                        Locale.US,
                        "Lag-1 residual autocorrelation is notable (r₁ = %.2f). Positive serial correlation can overstate standard Mann–Kendall significance.",
                        lag1,
                    ),
                )
            }
            if (config.trendMethod == TrendMethod.SEASONAL_MANN_KENDALL && config.aggregation == AggregationMode.ANNUAL_MEAN) {
                add("Seasonal Mann–Kendall requires sub-annual data; Standard Mann–Kendall was applied to annual means.")
            }
        }

        val chartTextSummary = buildString {
            append("Time-series chart of ${variable.displayName} ($unitLabel) for ${region.name} from $periodStart to $periodEnd. ")
            append("Contains $nValid valid observations and $nMissing missing/flagged gaps. ")
            append("Fitted ${config.slopeMethod.label} slope: $formattedSlope; p = $formattedP (${if (isSignificant) "statistically significant" else "not statistically significant"} at α = ${config.significanceAlpha}).")
        }

        val seasonalityHandling = when {
            config.aggregation == AggregationMode.DESEASONALIZED_ANOMALY && useSeasonalMk ->
                "Deseasonalized monthly anomalies paired with Seasonal Mann–Kendall."
            config.aggregation == AggregationMode.DESEASONALIZED_ANOMALY ->
                "Calendar-month climatological mean subtracted from each month."
            useSeasonalMk ->
                "Seasonal Mann–Kendall evaluates trends within each calendar month separately."
            config.aggregation == AggregationMode.ANNUAL_MEAN ->
                "Aggregated to annual means (>= 9 valid months required per year)."
            else ->
                "Raw monthly cadence without deseasonalization; seasonal amplitude remains in series."
        }

        val autocorrHandling = if (lag1 != null) {
            String.format(
                Locale.US,
                "Lag-1 residual autocorrelation r₁ = %.2f (%s).",
                lag1,
                if (hasAutocorrCaveat) "elevated; interpret p-value cautiously" else "within modest bounds",
            )
        } else {
            "Insufficient consecutive pairs to evaluate lag-1 autocorrelation."
        }

        val methodDisplay = if (useSeasonalMk) "Seasonal Mann–Kendall" else "Mann–Kendall"

        return VariableTrendResult(
            variable = variable,
            dataset = dataset,
            region = region,
            status = AnalysisStatus.SUCCESS,
            statusMessage = if (isSignificant) "Statistically significant trend" else "No statistically significant trend",
            trendDirection = direction,
            slopePerDecade = chosenSlopePerDecade,
            slopePerYear = chosenSlopePerYear,
            slopeUnit = variable.decadeSlopeUnit,
            interceptAtStart = trendLine.startValue,
            rSquared = ols.rSquared,
            ci95LowPerDecade = sen.ciLowPerDecade,
            ci95HighPerDecade = sen.ciHighPerDecade,
            pValue = mkResult.pValue,
            significanceThreshold = config.significanceAlpha,
            isStatisticallySignificant = isSignificant,
            zScore = mkResult.zScore,
            kendallTau = mkResult.tau,
            lag1Autocorrelation = lag1,
            hasAutocorrelationCaveat = hasAutocorrCaveat,
            nValidObservations = nValid,
            nTotalSlots = nTotalSlots,
            nMissingOrFlagged = nMissing,
            missingFraction = missingFraction,
            analysisPeriodStart = periodStart,
            analysisPeriodEnd = periodEnd,
            lastObservationDate = lastValidDate,
            methodName = methodDisplay,
            methodParameters = "α=${config.significanceAlpha}, slope=${config.slopeMethod.label}, aggregation=${config.aggregation.label}",
            slopeEstimatorName = config.slopeMethod.label,
            aggregationName = config.aggregation.label,
            seasonalityHandling = seasonalityHandling,
            autocorrelationHandling = autocorrHandling,
            dataSourceId = dataset.id,
            productVersion = dataset.productVersion,
            processingVersion = PROCESSING_VERSION,
            dataProvenanceStatus = provenanceStatus,
            retrievedAtIso = retrievedAtIso,
            series = aggregatedSeries,
            trendLine = trendLine,
            neutralTitle = neutralTitle,
            interpretationText = sigSentence,
            whatThisMeans = whatThisMeans,
            whatThisDoesNotMean = whatThisDoesNotMean,
            limitations = limitations,
            chartTextSummary = chartTextSummary,
        )
    }

    fun computeComparisonCorrelation(
        resultA: VariableTrendResult,
        resultB: VariableTrendResult,
    ): ComparisonCorrelation {
        val mapA = resultA.series
            .filter { it.value != null && it.qualityStatus == QualityStatus.VALID }
            .associate { it.yearMonth to it.value!! }
        val pairs = resultB.series
            .filter { it.value != null && it.qualityStatus == QualityStatus.VALID }
            .mapNotNull { pt -> mapA[pt.yearMonth]?.let { vA -> vA to pt.value!! } }

        if (pairs.size < 6) {
            return ComparisonCorrelation(
                labelA = resultA.variable.shortLabel,
                labelB = resultB.variable.shortLabel,
                pearsonR = null,
                overlapCount = pairs.size,
                caveat = "Insufficient overlapping valid dates (${pairs.size}) to compute correlation. Correlation does not establish causation.",
            )
        }

        val meanA = pairs.map { it.first }.average()
        val meanB = pairs.map { it.second }.average()
        var num = 0.0
        var denA = 0.0
        var denB = 0.0
        for ((a, b) in pairs) {
            val da = a - meanA
            val db = b - meanB
            num += da * db
            denA += da * da
            denB += db * db
        }
        val denom = sqrt(denA * denB)
        val r = if (denom > 1e-12) (num / denom).coerceIn(-1.0, 1.0) else null

        return ComparisonCorrelation(
            labelA = resultA.variable.shortLabel,
            labelB = resultB.variable.shortLabel,
            pearsonR = r,
            overlapCount = pairs.size,
            caveat = "Statistical association across ${pairs.size} overlapping valid slots only — correlation does not establish causation.",
        )
    }

    private fun applyAggregation(
        rawMonthly: List<TimeSeriesPoint>,
        mode: AggregationMode,
    ): List<TimeSeriesPoint> {
        return when (mode) {
            AggregationMode.MONTHLY -> rawMonthly
            AggregationMode.ANNUAL_MEAN -> {
                rawMonthly.groupBy { it.year }.toSortedMap().map { (year, points) ->
                    val valid = points.mapNotNull { if (it.qualityStatus == QualityStatus.VALID) it.value else null }
                    if (valid.size >= 9) {
                        val mean = valid.average()
                        TimeSeriesPoint(
                            yearMonth = year.toString(),
                            year = year,
                            month = 0,
                            decimalYear = year + 0.5,
                            rawValue = mean,
                            value = mean,
                            qualityStatus = QualityStatus.VALID,
                            sourceFlag = "ANNUAL_MEAN_N_${valid.size}",
                        )
                    } else {
                        TimeSeriesPoint(
                            yearMonth = year.toString(),
                            year = year,
                            month = 0,
                            decimalYear = year + 0.5,
                            rawValue = null,
                            value = null,
                            qualityStatus = QualityStatus.MISSING,
                            sourceFlag = "INSUFFICIENT_MONTHS_${valid.size}",
                        )
                    }
                }
            }
            AggregationMode.DESEASONALIZED_ANOMALY -> {
                val monthMeans = (1..12).associateWith { month ->
                    val vals = rawMonthly
                        .filter { it.month == month && it.qualityStatus == QualityStatus.VALID && it.value != null }
                        .map { it.value!! }
                    if (vals.isNotEmpty()) vals.average() else null
                }
                rawMonthly.map { pt ->
                    val baseline = monthMeans[pt.month]
                    if (pt.qualityStatus == QualityStatus.VALID && pt.value != null && baseline != null) {
                        pt.copy(value = pt.value - baseline)
                    } else {
                        pt.copy(value = null)
                    }
                }
            }
        }
    }

    private data class OlsOutput(
        val slopePerYear: Double,
        val intercept: Double,
        val rSquared: Double,
    )

    private fun computeOls(points: List<TimeSeriesPoint>): OlsOutput {
        val n = points.size.toDouble()
        val meanT = points.sumOf { it.decimalYear } / n
        val meanY = points.sumOf { it.value!! } / n
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (pt in points) {
            val dt = pt.decimalYear - meanT
            val dy = pt.value!! - meanY
            sxx += dt * dt
            sxy += dt * dy
            syy += dy * dy
        }
        val slope = if (sxx > 1e-12) sxy / sxx else 0.0
        val intercept = meanY - slope * meanT
        val r2 = if (sxx > 1e-12 && syy > 1e-12) ((sxy * sxy) / (sxx * syy)).coerceIn(0.0, 1.0) else 0.0
        return OlsOutput(slopePerYear = slope, intercept = intercept, rSquared = r2)
    }

    private data class SenOutput(
        val slopePerYear: Double,
        val ciLowPerDecade: Double?,
        val ciHighPerDecade: Double?,
    )

    private fun computeSenSlopeAndCi(points: List<TimeSeriesPoint>): SenOutput {
        val slopes = ArrayList<Double>(points.size * (points.size - 1) / 2)
        for (i in 0 until points.size - 1) {
            val t1 = points[i].decimalYear
            val y1 = points[i].value!!
            for (j in i + 1 until points.size) {
                val dt = points[j].decimalYear - t1
                if (dt > 1e-6) {
                    slopes.add((points[j].value!! - y1) / dt)
                }
            }
        }
        if (slopes.isEmpty()) return SenOutput(0.0, null, null)
        slopes.sort()
        val slopePerYear = medianSorted(slopes)

        // 95% confidence interval for Sen's slope using MK variance
        val n = points.size
        val varS = computeMkVariance(points.map { it.value!! })
        if (varS <= 0.0 || slopes.size < 10) {
            return SenOutput(slopePerYear, null, null)
        }
        val z95 = 1.95996398454
        val cAlpha = z95 * sqrt(varS)
        val nPairs = slopes.size
        val lowIdx = floor((nPairs - cAlpha) / 2.0).toInt().coerceIn(0, nPairs - 1)
        val highIdx = (ceil((nPairs + cAlpha) / 2.0).toInt() - 1).coerceIn(0, nPairs - 1)
        return SenOutput(
            slopePerYear = slopePerYear,
            ciLowPerDecade = slopes[lowIdx] * 10.0,
            ciHighPerDecade = slopes[highIdx] * 10.0,
        )
    }

    private data class MkOutput(
        val sStat: Double,
        val variance: Double,
        val zScore: Double,
        val pValue: Double,
        val tau: Double,
    )

    private fun computeStandardMannKendall(points: List<TimeSeriesPoint>): MkOutput {
        val values = points.map { it.value!! }
        val n = values.size
        var s = 0.0
        for (i in 0 until n - 1) {
            for (j in i + 1 until n) {
                val diff = values[j] - values[i]
                if (abs(diff) > 1e-12) {
                    s += sign(diff)
                }
            }
        }
        val varS = computeMkVariance(values)
        val z = when {
            varS <= 0.0 -> 0.0
            s > 0.0 -> (s - 1.0) / sqrt(varS)
            s < 0.0 -> (s + 1.0) / sqrt(varS)
            else -> 0.0
        }
        val p = twoSidedNormalPValue(z)
        val denom = 0.5 * n * (n - 1)
        val tau = if (denom > 0.0) s / denom else 0.0
        return MkOutput(sStat = s, variance = varS, zScore = z, pValue = p, tau = tau)
    }

    private fun computeSeasonalMannKendall(points: List<TimeSeriesPoint>): MkOutput {
        var totalS = 0.0
        var totalVar = 0.0
        var totalPairs = 0.0
        val byMonth = points.groupBy { it.month }
        for (month in 1..12) {
            val seasonPts = byMonth[month]?.sortedBy { it.year } ?: continue
            if (seasonPts.size < 2) continue
            val values = seasonPts.map { it.value!! }
            val n = values.size
            var s = 0.0
            for (i in 0 until n - 1) {
                for (j in i + 1 until n) {
                    val diff = values[j] - values[i]
                    if (abs(diff) > 1e-12) {
                        s += sign(diff)
                    }
                }
            }
            totalS += s
            totalVar += computeMkVariance(values)
            totalPairs += 0.5 * n * (n - 1)
        }
        val z = when {
            totalVar <= 0.0 -> 0.0
            totalS > 0.0 -> (totalS - 1.0) / sqrt(totalVar)
            totalS < 0.0 -> (totalS + 1.0) / sqrt(totalVar)
            else -> 0.0
        }
        val p = twoSidedNormalPValue(z)
        val tau = if (totalPairs > 0.0) totalS / totalPairs else 0.0
        return MkOutput(sStat = totalS, variance = totalVar, zScore = z, pValue = p, tau = tau)
    }

    private fun computeMkVariance(values: List<Double>): Double {
        val n = values.size.toDouble()
        if (n < 2) return 0.0
        // Group ties with tolerance 1e-9
        val sorted = values.sorted()
        var tieCorrection = 0.0
        var idx = 0
        while (idx < sorted.size) {
            var count = 1
            while (idx + count < sorted.size && abs(sorted[idx + count] - sorted[idx]) < 1e-9) {
                count++
            }
            if (count > 1) {
                val tp = count.toDouble()
                tieCorrection += tp * (tp - 1.0) * (2.0 * tp + 5.0)
            }
            idx += count
        }
        return ((n * (n - 1.0) * (2.0 * n + 5.0)) - tieCorrection) / 18.0
    }

    private fun computeLag1Autocorrelation(
        series: List<TimeSeriesPoint>,
        slopePerYear: Double,
        trendLine: TrendLineEndpoint,
    ): Double? {
        val span = (trendLine.endYear - trendLine.startYear).coerceAtLeast(1e-6)
        val intercept = trendLine.startValue - slopePerYear * trendLine.startYear
        val residuals = series.map { pt ->
            if (pt.qualityStatus == QualityStatus.VALID && pt.value != null) {
                pt.value - (intercept + slopePerYear * pt.decimalYear)
            } else {
                null
            }
        }
        val consecutivePairs = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until residuals.size - 1) {
            val r0 = residuals[i]
            val r1 = residuals[i + 1]
            if (r0 != null && r1 != null) {
                consecutivePairs.add(r0 to r1)
            }
        }
        if (consecutivePairs.size < 8) return null
        val validAll = residuals.filterNotNull()
        val mean = validAll.average()
        var num = 0.0
        var den = 0.0
        for ((a, b) in consecutivePairs) {
            num += (a - mean) * (b - mean)
        }
        for (r in validAll) {
            val d = r - mean
            den += d * d
        }
        if (den < 1e-12 || span <= 0.0) return null
        return (num / den).coerceIn(-1.0, 1.0)
    }

    private fun twoSidedNormalPValue(z: Double): Double {
        val absZ = abs(z)
        // P(|Z| >= |z|) = erfc(|z| / sqrt(2))
        return erfcApprox(absZ / sqrt(2.0)).coerceIn(0.0, 1.0)
    }

    /** Abramowitz & Stegun 7.1.26 complementary error function approximation (max error 1.5e-7). */
    private fun erfcApprox(x: Double): Double {
        val t = 1.0 / (1.0 + 0.3275911 * abs(x))
        val poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
        val erfcVal = poly * exp(-x.pow(2.0))
        return if (x >= 0.0) erfcVal else 2.0 - erfcVal
    }

    private fun median(values: List<Double>): Double = medianSorted(values.sorted())

    private fun medianSorted(sorted: List<Double>): Double {
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else 0.5 * (sorted[n / 2 - 1] + sorted[n / 2])
    }

    fun formatPValue(p: Double?): String {
        if (p == null) return "N/A"
        return when {
            p < 0.0001 -> "< 0.0001"
            p < 0.001 -> String.format(Locale.US, "%.4f", p)
            else -> String.format(Locale.US, "%.3f", p)
        }
    }

    private fun buildInsufficientResult(
        variable: VariableDefinition,
        dataset: DatasetMetadata,
        region: StudyRegion,
        config: InvestigationConfig,
        provenanceStatus: DataProvenanceStatus,
        retrievedAtIso: String,
        series: List<TimeSeriesPoint>,
        status: AnalysisStatus,
        reason: String,
    ): VariableTrendResult {
        val nValid = series.count { it.qualityStatus == QualityStatus.VALID && it.value != null }
        val nTotal = series.size
        val nMissing = nTotal - nValid
        val missingFrac = if (nTotal > 0) nMissing.toDouble() / nTotal.toDouble() else 1.0
        return VariableTrendResult(
            variable = variable,
            dataset = dataset,
            region = region,
            status = status,
            statusMessage = reason,
            trendDirection = TrendDirection.NO_CLEAR_DIRECTION,
            slopePerDecade = null,
            slopePerYear = null,
            slopeUnit = variable.decadeSlopeUnit,
            interceptAtStart = null,
            rSquared = null,
            ci95LowPerDecade = null,
            ci95HighPerDecade = null,
            pValue = null,
            significanceThreshold = config.significanceAlpha,
            isStatisticallySignificant = false,
            zScore = null,
            kendallTau = null,
            lag1Autocorrelation = null,
            hasAutocorrelationCaveat = false,
            nValidObservations = nValid,
            nTotalSlots = nTotal,
            nMissingOrFlagged = nMissing,
            missingFraction = missingFrac,
            analysisPeriodStart = series.firstOrNull()?.yearMonth ?: "${config.startYear}-01",
            analysisPeriodEnd = series.lastOrNull()?.yearMonth ?: "${config.endYear}-12",
            lastObservationDate = series.lastOrNull { it.qualityStatus == QualityStatus.VALID }?.yearMonth ?: "Unavailable",
            methodName = config.trendMethod.label,
            methodParameters = "α=${config.significanceAlpha}, aggregation=${config.aggregation.label}",
            slopeEstimatorName = config.slopeMethod.label,
            aggregationName = config.aggregation.label,
            seasonalityHandling = "Skipped due to insufficient valid sample coverage.",
            autocorrelationHandling = "Skipped due to insufficient valid sample coverage.",
            dataSourceId = dataset.id,
            productVersion = dataset.productVersion,
            processingVersion = PROCESSING_VERSION,
            dataProvenanceStatus = provenanceStatus,
            retrievedAtIso = retrievedAtIso,
            series = series,
            trendLine = null,
            neutralTitle = "${variable.displayName} in ${region.name}: insufficient data for trend estimate",
            interpretationText = reason,
            whatThisMeans = reason,
            whatThisDoesNotMean = "An aborted or insufficient-data result does not mean no environmental change occurred—it indicates missing or short records prevent a reliable statistical estimate.",
            limitations = listOf(dataset.limitations, reason),
            chartTextSummary = "Chart displays available observations and marked missing intervals for ${variable.displayName} in ${region.name}. Trend line omitted to prevent misleading extrapolation.",
        )
    }
}
