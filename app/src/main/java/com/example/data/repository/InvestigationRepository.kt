package com.example.data.repository

import com.example.data.catalog.NasaCatalog
import com.example.data.local.SavedInvestigationDao
import com.example.data.local.SavedInvestigationEntity
import com.example.data.local.VerifiedNasaArchive
import com.example.data.model.AnalysisStatus
import com.example.data.model.DataProvenanceStatus
import com.example.data.model.DataSourceMode
import com.example.data.model.InvestigationConfig
import com.example.data.model.InvestigationResult
import com.example.data.model.QualityStatus
import com.example.data.model.SavedDiscoverySummary
import com.example.data.model.StudyRegion
import com.example.data.model.TimeSeriesPoint
import com.example.data.model.TrendDirection
import com.example.data.model.VariableDefinition
import com.example.data.model.VariableTrendResult
import com.example.data.remote.NasaPowerClient
import com.example.domain.analysis.TrendAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class InvestigationRepository(
    private val context: android.content.Context,
    private val savedDao: SavedInvestigationDao,
) {

    fun observeSavedDiscoveries(): Flow<List<SavedDiscoverySummary>> =
        savedDao.observeAll().map { entities ->
            entities.map { e ->
                val direction = TrendDirection.entries.firstOrNull { it.name == e.directionName }
                    ?: TrendDirection.NO_CLEAR_DIRECTION
                SavedDiscoverySummary(
                    id = e.id,
                    title = e.title,
                    regionName = e.regionName,
                    primaryVariableName = e.primaryVariableName,
                    variableIds = listOfNotNull(e.primaryVariableId, e.secondaryVariableId),
                    periodLabel = "${e.startYear}–${e.endYear}",
                    direction = direction,
                    isSignificant = e.isSignificant,
                    slopeFormatted = e.slopeFormatted,
                    pValueFormatted = e.pValueFormatted,
                    savedAtIso = e.savedAtIso,
                    isPreloadedReference = false,
                    resultJson = e.resultJson,
                )
            }
        }

    suspend fun runInvestigation(config: InvestigationConfig): InvestigationResult =
        withContext(Dispatchers.IO) {
            val primaryVar = NasaCatalog.variableFor(config.variableIds.first())
            val primaryResult = fetchAndAnalyzeVariable(
                region = config.region,
                variable = primaryVar,
                config = config,
            )

            val secondaryVar = config.variableIds.getOrNull(1)?.let { NasaCatalog.variableFor(it) }
            val secondaryResult = secondaryVar?.let { sec ->
                fetchAndAnalyzeVariable(
                    region = config.region,
                    variable = sec,
                    config = config,
                )
            }

            val compRegionResult = config.comparisonRegion?.let { compRegion ->
                fetchAndAnalyzeVariable(
                    region = compRegion,
                    variable = primaryVar,
                    config = config.copy(region = compRegion),
                )
            }

            val correlation = if (secondaryResult != null &&
                primaryResult.status == AnalysisStatus.SUCCESS &&
                secondaryResult.status == AnalysisStatus.SUCCESS
            ) {
                TrendAnalyzer.computeComparisonCorrelation(primaryResult, secondaryResult)
            } else {
                null
            }

            InvestigationResult(
                config = config,
                primaryResult = primaryResult,
                secondaryResult = secondaryResult,
                comparisonRegionResult = compRegionResult,
                comparisonCorrelation = correlation,
                computedAtIso = nowIsoUtc(),
            )
        }

    private suspend fun fetchAndAnalyzeVariable(
        region: StudyRegion,
        variable: VariableDefinition,
        config: InvestigationConfig,
    ): VariableTrendResult {
        val dataset = NasaCatalog.datasetFor(variable.datasetId)
        val nowIso = nowIsoUtc()

        var provenance = DataProvenanceStatus.CACHED_NASA_REFERENCE
        var retrievedAt = VerifiedNasaArchive.ARCHIVE_SNAPSHOT_TIMESTAMP
        var points: List<TimeSeriesPoint> = emptyList()
        var liveErrorDetail: String? = null

        val shouldTryLive = config.dataSourceMode == DataSourceMode.AUTO_LIVE_WITH_CACHE &&
            variable.nasaPowerParam != null

        if (shouldTryLive) {
            try {
                val response = NasaPowerClient.api.getMonthlyPoint(
                    parameters = variable.nasaPowerParam!!,
                    longitude = region.centroidLon,
                    latitude = region.centroidLat,
                    startYear = config.startYear,
                    endYear = config.endYear,
                )
                val parsed = NasaPowerClient.parseMonthlyParameter(response, variable)
                if (parsed.isNotEmpty()) {
                    points = parsed
                    provenance = DataProvenanceStatus.LIVE_NASA_POWER
                    retrievedAt = nowIso
                } else {
                    liveErrorDetail = "NASA POWER API returned an empty parameter series."
                }
            } catch (e: Exception) {
                liveErrorDetail = e.localizedMessage ?: "Network request to NASA POWER failed."
            }
        }

        if (points.isEmpty()) {
            if (VerifiedNasaArchive.hasCachedSeries(region.id)) {
                points = VerifiedNasaArchive.getCachedSeries(
                    context = context,
                    regionId = region.id,
                    variable = variable,
                    startYear = config.startYear,
                    endYear = config.endYear,
                )
                provenance = if (liveErrorDetail != null) {
                    DataProvenanceStatus.OFFLINE_CACHE_FALLBACK
                } else {
                    DataProvenanceStatus.CACHED_NASA_REFERENCE
                }
                retrievedAt = VerifiedNasaArchive.ARCHIVE_SNAPSHOT_TIMESTAMP
            } else {
                // Custom coordinate when offline or non-POWER variable selected
                val reason = when {
                    variable.nasaPowerParam == null ->
                        "${variable.displayName} (${dataset.shortName}) requires a curated study region in the cached NASA archive."
                    else ->
                        "Unable to reach live NASA POWER API for custom coordinate ${region.name} (${liveErrorDetail ?: "offline"}). Connect to the internet or select one of the curated cached study regions."
                }
                return TrendAnalyzer.analyze(
                    rawPoints = emptyList(),
                    variable = variable,
                    dataset = dataset,
                    region = region,
                    config = config,
                    provenanceStatus = DataProvenanceStatus.OFFLINE_CACHE_FALLBACK,
                    retrievedAtIso = nowIso,
                ).copy(
                    status = AnalysisStatus.ERROR,
                    statusMessage = reason,
                    neutralTitle = "Live NASA query unavailable for ${region.name}",
                    interpretationText = reason,
                    whatThisMeans = reason,
                )
            }
        }

        return TrendAnalyzer.analyze(
            rawPoints = points,
            variable = variable,
            dataset = dataset,
            region = region,
            config = config,
            provenanceStatus = provenance,
            retrievedAtIso = retrievedAt,
        )
    }

    suspend fun saveInvestigation(result: InvestigationResult): SavedDiscoverySummary =
        withContext(Dispatchers.IO) {
            val primary = result.primaryResult
            val slopeFmt = primary.slopePerDecade?.let {
                String.format(Locale.US, "%+.2f %s", it, primary.slopeUnit)
            } ?: "N/A"
            val pFmt = TrendAnalyzer.formatPValue(primary.pValue)
            val savedAt = nowIsoUtc()
            val id = result.config.id.ifBlank { UUID.randomUUID().toString() }
            val json = exportJson(result)

            val entity = SavedInvestigationEntity(
                id = id,
                title = result.config.title.ifBlank { primary.neutralTitle },
                regionId = primary.region.id,
                regionName = primary.region.name,
                primaryVariableId = primary.variable.id,
                primaryVariableName = primary.variable.displayName,
                secondaryVariableId = result.secondaryResult?.variable?.id,
                startYear = result.config.startYear,
                endYear = result.config.endYear,
                directionName = primary.trendDirection.name,
                isSignificant = primary.isStatisticallySignificant,
                slopeFormatted = slopeFmt,
                pValueFormatted = pFmt,
                savedAtIso = savedAt,
                resultJson = json,
            )
            savedDao.insert(entity)

            SavedDiscoverySummary(
                id = entity.id,
                title = entity.title,
                regionName = entity.regionName,
                primaryVariableName = entity.primaryVariableName,
                variableIds = listOfNotNull(entity.primaryVariableId, entity.secondaryVariableId),
                periodLabel = "${entity.startYear}–${entity.endYear}",
                direction = primary.trendDirection,
                isSignificant = entity.isSignificant,
                slopeFormatted = slopeFmt,
                pValueFormatted = pFmt,
                savedAtIso = savedAt,
                isPreloadedReference = false,
                resultJson = json,
            )
        }

    suspend fun deleteSavedDiscovery(id: String) = withContext(Dispatchers.IO) {
        savedDao.deleteById(id)
    }

    fun buildConfigFromSavedSummary(summary: SavedDiscoverySummary): InvestigationConfig? {
        return try {
            val root = JSONObject(summary.resultJson)
            val cfg = root.getJSONObject("config")
            val regionObj = cfg.getJSONObject("region")
            val regionId = regionObj.getString("id")
            val region = NasaCatalog.regions.firstOrNull { it.id == regionId }
                ?: NasaCatalog.createCustomRegion(
                    regionObj.getDouble("centroid_lat"),
                    regionObj.getDouble("centroid_lon"),
                )
            InvestigationConfig(
                id = summary.id,
                title = summary.title,
                region = region,
                variableIds = summary.variableIds,
                startYear = cfg.getInt("start_year"),
                endYear = cfg.getInt("end_year"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun exportCsv(result: InvestigationResult): String {
        val sb = StringBuilder()
        val p = result.primaryResult
        sb.appendLine("# EARTH PULSE Export Report (NASA Space Apps Challenge)")
        sb.appendLine("# Region: ${p.region.name} (${p.region.centroidLat}, ${p.region.centroidLon})")
        sb.appendLine("# Period: ${result.config.startYear}-${result.config.endYear}")
        sb.appendLine("# Primary Dataset: ${p.dataset.name} (${p.productVersion})")
        sb.appendLine("# Provenance: ${p.dataProvenanceStatus.label} | Retrieved: ${p.retrievedAtIso}")
        sb.appendLine("# Method: ${p.methodName} | Slope: ${p.slopeEstimatorName} | Aggregation: ${p.aggregationName}")
        sb.appendLine("# Slope/Decade: ${p.slopePerDecade ?: "NA"} ${p.slopeUnit} | p-value: ${p.pValue ?: "NA"} | Significant(alpha=${p.significanceThreshold}): ${p.isStatisticallySignificant}")
        sb.appendLine("# Processing Version: ${p.processingVersion}")
        sb.appendLine("variable_id,variable_name,unit,date,value,quality_status,source_flag")

        fun appendSeries(res: VariableTrendResult) {
            for (pt in res.series) {
                val valStr = if (pt.qualityStatus == QualityStatus.VALID && pt.value != null) {
                    String.format(Locale.US, "%.4f", pt.value)
                } else {
                    "NA"
                }
                sb.appendLine("${res.variable.id},\"${res.variable.displayName}\",\"${res.variable.unit}\",${pt.yearMonth},$valStr,${pt.qualityStatus.name},${pt.sourceFlag}")
            }
        }
        appendSeries(p)
        result.secondaryResult?.let { appendSeries(it) }
        return sb.toString()
    }

    fun exportJson(result: InvestigationResult): String {
        val p = result.primaryResult
        val root = JSONObject()
        root.put("platform", "EARTH PULSE")
        root.put("computed_at", result.computedAtIso)

        val cfg = JSONObject()
        cfg.put("id", result.config.id)
        cfg.put("start_year", result.config.startYear)
        cfg.put("end_year", result.config.endYear)
        cfg.put("aggregation", result.config.aggregation.name)
        cfg.put("trend_method", result.config.trendMethod.name)
        cfg.put("slope_method", result.config.slopeMethod.name)
        cfg.put("alpha", result.config.significanceAlpha)

        val reg = JSONObject()
        reg.put("id", p.region.id)
        reg.put("name", p.region.name)
        reg.put("centroid_lat", p.region.centroidLat)
        reg.put("centroid_lon", p.region.centroidLon)
        cfg.put("region", reg)
        root.put("config", cfg)

        fun serializeResult(res: VariableTrendResult): JSONObject {
            val obj = JSONObject()
            obj.put("variable_id", res.variable.id)
            obj.put("variable_name", res.variable.displayName)
            obj.put("unit", res.variable.unit)
            obj.put("dataset_id", res.dataSourceId)
            obj.put("product_version", res.productVersion)
            obj.put("processing_version", res.processingVersion)
            obj.put("provenance_status", res.dataProvenanceStatus.label)
            obj.put("retrieved_at", res.retrievedAtIso)
            obj.put("status", res.status.name)
            obj.put("trend_direction", res.trendDirection.name)
            obj.put("slope_per_decade", res.slopePerDecade ?: JSONObject.NULL)
            obj.put("slope_unit", res.slopeUnit)
            obj.put("ci95_low_per_decade", res.ci95LowPerDecade ?: JSONObject.NULL)
            obj.put("ci95_high_per_decade", res.ci95HighPerDecade ?: JSONObject.NULL)
            obj.put("p_value", res.pValue ?: JSONObject.NULL)
            obj.put("is_statistically_significant", res.isStatisticallySignificant)
            obj.put("n_valid", res.nValidObservations)
            obj.put("n_total_slots", res.nTotalSlots)
            obj.put("missing_fraction", res.missingFraction)
            obj.put("interpretation", res.interpretationText)
            val obsArr = JSONArray()
            res.series.forEach { pt ->
                val pObj = JSONObject()
                pObj.put("date", pt.yearMonth)
                pObj.put("value", pt.value ?: JSONObject.NULL)
                pObj.put("quality_status", pt.qualityStatus.name)
                obsArr.put(pObj)
            }
            obj.put("observations", obsArr)
            return obj
        }

        root.put("primary_result", serializeResult(p))
        result.secondaryResult?.let { root.put("secondary_result", serializeResult(it)) }
        result.comparisonCorrelation?.let { corr ->
            val cObj = JSONObject()
            cObj.put("pearson_r", corr.pearsonR ?: JSONObject.NULL)
            cObj.put("overlap_count", corr.overlapCount)
            cObj.put("caveat", corr.caveat)
            root.put("comparison_correlation", cObj)
        }
        return root.toString(2)
    }

    private fun nowIsoUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
