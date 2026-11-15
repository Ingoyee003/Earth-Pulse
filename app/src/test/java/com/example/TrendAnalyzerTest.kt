package com.example

import com.example.data.catalog.NasaCatalog
import com.example.data.model.AggregationMode
import com.example.data.model.AnalysisStatus
import com.example.data.model.DataProvenanceStatus
import com.example.data.model.InvestigationConfig
import com.example.data.model.QualityStatus
import com.example.data.model.SlopeMethod
import com.example.data.model.TimeSeriesPoint
import com.example.data.model.TrendDirection
import com.example.data.model.TrendMethod
import com.example.domain.analysis.TrendAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class TrendAnalyzerTest {

    private val testVar = NasaCatalog.variableFor("T2M")
    private val testDataset = NasaCatalog.datasetFor(testVar.datasetId)
    private val testRegion = NasaCatalog.regionFor("svalbard_arctic")

    private fun syntheticMonthlySeries(
        startYear: Int = 2000,
        endYear: Int = 2019,
        slopePerYear: Double = 0.0,
        seasonalAmp: Double = 0.0,
        fillMonths: Set<Pair<Int, Int>> = emptySet(),
        autocorrStrength: Double = 0.0,
    ): List<TimeSeriesPoint> {
        val list = mutableListOf<TimeSeriesPoint>()
        var prevResidual = 0.0
        for (year in startYear..endYear) {
            for (month in 1..12) {
                val decimalYear = year + (month - 0.5) / 12.0
                val isMissing = (year to month) in fillMonths
                if (isMissing) {
                    list.add(
                        TimeSeriesPoint(
                            yearMonth = String.format(Locale.US, "%04d-%02d", year, month),
                            year = year,
                            month = month,
                            decimalYear = decimalYear,
                            rawValue = -999.0,
                            value = null,
                            qualityStatus = QualityStatus.VALID, // let QC detect -999.0 fill!
                            sourceFlag = "SYNTHETIC_FILL",
                        ),
                    )
                } else {
                    val noise = autocorrStrength * prevResidual + 0.05 * sin((year * 7 + month * 3).toDouble())
                    prevResidual = noise
                    val seasonal = seasonalAmp * sin(2.0 * PI * (month - 1) / 12.0)
                    val valComputed = 10.0 + slopePerYear * (decimalYear - startYear) + seasonal + noise
                    list.add(
                        TimeSeriesPoint(
                            yearMonth = String.format(Locale.US, "%04d-%02d", year, month),
                            year = year,
                            month = month,
                            decimalYear = decimalYear,
                            rawValue = valComputed,
                            value = valComputed,
                            qualityStatus = QualityStatus.VALID,
                            sourceFlag = "SYNTHETIC_TEST_FIXTURE",
                        ),
                    )
                }
            }
        }
        return list
    }

    @Test
    fun `missing and -999 fill values are never converted to zero`() {
        val fillSet = setOf(2005 to 3, 2005 to 4, 2010 to 7)
        val raw = syntheticMonthlySeries(
            startYear = 2000,
            endYear = 2015,
            slopePerYear = 0.1,
            fillMonths = fillSet,
        )
        val config = InvestigationConfig(
            id = "test_missing",
            title = "Missing test",
            region = testRegion,
            variableIds = listOf("T2M"),
            startYear = 2000,
            endYear = 2015,
            aggregation = AggregationMode.MONTHLY,
            trendMethod = TrendMethod.MANN_KENDALL,
        )

        val result = TrendAnalyzer.analyze(
            rawPoints = raw,
            variable = testVar,
            dataset = testDataset,
            region = testRegion,
            config = config,
            provenanceStatus = DataProvenanceStatus.CACHED_NASA_REFERENCE,
            retrievedAtIso = "2026-01-01T00:00:00Z",
        )

        assertEquals(AnalysisStatus.SUCCESS, result.status)
        assertEquals(3, result.nMissingOrFlagged)
        val gapPoint = result.series.first { it.yearMonth == "2005-03" }
        assertEquals(QualityStatus.MISSING, gapPoint.qualityStatus)
        assertNull(gapPoint.value)
    }

    @Test
    fun `known increasing synthetic series yields significant positive slope`() {
        val raw = syntheticMonthlySeries(startYear = 2000, endYear = 2019, slopePerYear = 0.08) // +0.8 per decade
        val config = InvestigationConfig(
            id = "test_inc",
            title = "Increasing test",
            region = testRegion,
            variableIds = listOf("T2M"),
            startYear = 2000,
            endYear = 2019,
            aggregation = AggregationMode.MONTHLY,
            trendMethod = TrendMethod.MANN_KENDALL,
            slopeMethod = SlopeMethod.SENS_SLOPE,
        )
        val result = TrendAnalyzer.analyze(
            rawPoints = raw,
            variable = testVar,
            dataset = testDataset,
            region = testRegion,
            config = config,
            provenanceStatus = DataProvenanceStatus.CACHED_NASA_REFERENCE,
            retrievedAtIso = "2026-01-01T00:00:00Z",
        )

        assertEquals(TrendDirection.INCREASING, result.trendDirection)
        assertTrue(result.isStatisticallySignificant)
        assertNotNull(result.slopePerDecade)
        assertTrue(abs(result.slopePerDecade!! - 0.8) < 0.08)
        assertTrue((result.pValue ?: 1.0) < 0.001)
    }

    @Test
    fun `known decreasing synthetic series yields significant negative slope`() {
        val raw = syntheticMonthlySeries(startYear = 2000, endYear = 2019, slopePerYear = -0.06) // -0.6 per decade
        val config = InvestigationConfig(
            id = "test_dec",
            title = "Decreasing test",
            region = testRegion,
            variableIds = listOf("T2M"),
            startYear = 2000,
            endYear = 2019,
            aggregation = AggregationMode.MONTHLY,
            trendMethod = TrendMethod.MANN_KENDALL,
            slopeMethod = SlopeMethod.OLS_LINEAR,
        )
        val result = TrendAnalyzer.analyze(
            rawPoints = raw,
            variable = testVar,
            dataset = testDataset,
            region = testRegion,
            config = config,
            provenanceStatus = DataProvenanceStatus.CACHED_NASA_REFERENCE,
            retrievedAtIso = "2026-01-01T00:00:00Z",
        )

        assertEquals(TrendDirection.DECREASING, result.trendDirection)
        assertTrue(result.isStatisticallySignificant)
        assertNotNull(result.slopePerDecade)
        assertTrue(abs(result.slopePerDecade!! - (-0.6)) < 0.08)
    }

    @Test
    fun `flat stationary series is not statistically significant and never claims zero physical change`() {
        val raw = syntheticMonthlySeries(startYear = 2000, endYear = 2019, slopePerYear = 0.0)
        val config = InvestigationConfig(
            id = "test_flat",
            title = "No trend test",
            region = testRegion,
            variableIds = listOf("T2M"),
            startYear = 2000,
            endYear = 2019,
            aggregation = AggregationMode.MONTHLY,
            trendMethod = TrendMethod.MANN_KENDALL,
        )
        val result = TrendAnalyzer.analyze(
            rawPoints = raw,
            variable = testVar,
            dataset = testDataset,
            region = testRegion,
            config = config,
            provenanceStatus = DataProvenanceStatus.CACHED_NASA_REFERENCE,
            retrievedAtIso = "2026-01-01T00:00:00Z",
        )

        assertFalse(result.isStatisticallySignificant)
        assertTrue(result.interpretationText.contains("does not provide sufficient evidence"))
    }

    @Test
    fun `seasonal series uses Seasonal Mann Kendall to recover subtle trend masked by large seasonal cycle`() {
        val raw = syntheticMonthlySeries(
            startYear = 2000,
            endYear = 2019,
            slopePerYear = 0.03, // +0.3 per decade
            seasonalAmp = 12.0,   // huge 24 deg peak-to-trough swing
        )
        val config = InvestigationConfig(
            id = "test_seasonal",
            title = "Seasonal MK test",
            region = testRegion,
            variableIds = listOf("T2M"),
            startYear = 2000,
            endYear = 2019,
            aggregation = AggregationMode.DESEASONALIZED_ANOMALY,
            trendMethod = TrendMethod.SEASONAL_MANN_KENDALL,
        )
        val result = TrendAnalyzer.analyze(
            rawPoints = raw,
            variable = testVar,
            dataset = testDataset,
            region = testRegion,
            config = config,
            provenanceStatus = DataProvenanceStatus.CACHED_NASA_REFERENCE,
            retrievedAtIso = "2026-01-01T00:00:00Z",
        )

        assertTrue(result.isStatisticallySignificant)
        assertEquals(TrendDirection.INCREASING, result.trendDirection)
        assertEquals("Seasonal Mann–Kendall", result.methodName)
        assertEquals(testDataset.id, result.dataSourceId)
        assertEquals(TrendAnalyzer.PROCESSING_VERSION, result.processingVersion)
    }

    @Test
    fun `insufficient observations triggers recovery status instead of misleading trend`() {
        val raw = syntheticMonthlySeries(startYear = 2020, endYear = 2020, slopePerYear = 0.5)
        // Only 8 valid months, 4 missing
        val truncated = raw.take(8)
        val config = InvestigationConfig(
            id = "test_short",
            title = "Short test",
            region = testRegion,
            variableIds = listOf("T2M"),
            startYear = 2020,
            endYear = 2020,
            aggregation = AggregationMode.MONTHLY,
            trendMethod = TrendMethod.MANN_KENDALL,
        )
        val result = TrendAnalyzer.analyze(
            rawPoints = truncated,
            variable = testVar,
            dataset = testDataset,
            region = testRegion,
            config = config,
            provenanceStatus = DataProvenanceStatus.CACHED_NASA_REFERENCE,
            retrievedAtIso = "2026-01-01T00:00:00Z",
        )

        assertEquals(AnalysisStatus.INSUFFICIENT_DATA, result.status)
        assertNull(result.slopePerDecade)
        assertNull(result.trendLine)
    }

    @Test
    fun `autocorrelated series flags significant lag-1 autocorrelation in diagnostics`() {
        val raw = syntheticMonthlySeries(
            startYear = 2000,
            endYear = 2019,
            slopePerYear = 0.04,
            autocorrStrength = 0.75,
        )
        val config = InvestigationConfig(
            id = "test_autocorr",
            title = "Autocorrelation test",
            region = testRegion,
            variableIds = listOf("T2M"),
            startYear = 2000,
            endYear = 2019,
            aggregation = AggregationMode.MONTHLY,
            trendMethod = TrendMethod.MANN_KENDALL,
        )
        val result = TrendAnalyzer.analyze(
            rawPoints = raw,
            variable = testVar,
            dataset = testDataset,
            region = testRegion,
            config = config,
            provenanceStatus = DataProvenanceStatus.CACHED_NASA_REFERENCE,
            retrievedAtIso = "2026-01-01T00:00:00Z",
        )

        assertEquals(AnalysisStatus.SUCCESS, result.status)
        assertTrue(result.hasAutocorrelationCaveat)
        assertNotNull(result.lag1Autocorrelation)
    }
}
