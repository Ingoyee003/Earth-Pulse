package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class DatasetMetadata(
    val id: String,
    val name: String,
    val shortName: String,
    val provider: String,
    val missionOrModel: String,
    val productVersion: String,
    val processingLevel: String,
    val spatialResolution: String,
    val coordinateSystem: String,
    val temporalResolution: String,
    val coverageStartYear: Int,
    val coverageEndYear: Int,
    val latencyNote: String,
    val fillValueDescription: String,
    val qualityFlagDescription: String,
    val accessMethod: String,
    val documentationUrl: String,
    val citationText: String,
    val limitations: String,
)

@Immutable
data class VariableDefinition(
    val id: String,
    val datasetId: String,
    val code: String,
    val nasaPowerParam: String?, // non-null if supported by live NASA POWER monthly point API
    val displayName: String,
    val shortLabel: String,
    val plainDescription: String,
    val unit: String,
    val decadeSlopeUnit: String,
    val validMin: Double,
    val validMax: Double,
    val scaleFactor: Double,
    val availableStartYear: Int,
    val availableEndYear: Int,
    val defaultAggregationNote: String,
    val scientificCaveat: String,
)

@Immutable
data class StudyRegion(
    val id: String,
    val name: String,
    val countryOrArea: String,
    val regionType: String,
    val centroidLat: Double,
    val centroidLon: Double,
    val bboxMinLat: Double,
    val bboxMaxLat: Double,
    val bboxMinLon: Double,
    val bboxMaxLon: Double,
    val areaKm2: Int,
    val summaryDescription: String,
    val isCustomPoint: Boolean = false,
)

@Immutable
data class FeaturedInvestigation(
    val id: String,
    val title: String,
    val subtitle: String,
    val regionId: String,
    val variableIds: List<String>,
    val startYear: Int,
    val endYear: Int,
    val aggregation: AggregationMode,
    val trendMethod: TrendMethod,
    val badgeLabel: String = "DEMO",
)

@Immutable
data class GlossaryTerm(
    val term: String,
    val definition: String,
    val earthPulseNote: String,
)

enum class QualityStatus {
    VALID,
    MISSING,
    FLAGGED_CLOUD,
    OUT_OF_RANGE,
}

@Immutable
data class TimeSeriesPoint(
    val yearMonth: String, // "YYYY-MM" or "YYYY"
    val year: Int,
    val month: Int, // 1..12, or 0 for annual
    val decimalYear: Double,
    val rawValue: Double?,
    val value: Double?, // null whenever missing/flagged/out-of-range (never zero-filled)
    val qualityStatus: QualityStatus,
    val sourceFlag: String,
)

enum class AggregationMode(val label: String, val description: String) {
    MONTHLY("Monthly observations", "Retains full monthly cadence; pair with Seasonal Mann–Kendall."),
    DESEASONALIZED_ANOMALY("Seasonal anomaly", "Subtracts each calendar month's baseline mean to reveal underlying shift."),
    ANNUAL_MEAN("Annual mean", "Averages calendar years with >= 9 valid months to reduce seasonal noise."),
}

enum class TrendMethod(val label: String, val description: String) {
    SEASONAL_MANN_KENDALL("Seasonal Mann–Kendall", "Non-parametric test evaluating monotonic change within each calendar month."),
    MANN_KENDALL("Standard Mann–Kendall", "Non-parametric rank test across the aggregated time series."),
}

enum class SlopeMethod(val label: String) {
    SENS_SLOPE("Theil–Sen robust slope"),
    OLS_LINEAR("Ordinary least-squares (OLS)"),
}

enum class DataSourceMode(val label: String, val description: String) {
    AUTO_LIVE_WITH_CACHE("Live NASA POWER + Cached Reference", "Queries NASA POWER API when online for supported variables, or uses verified local NASA reference records."),
    CACHED_VERIFIED_ONLY("Verified NASA Cached Archive", "Uses preloaded verified NASA regional records for instant offline reproducibility."),
}

enum class DataProvenanceStatus(val label: String) {
    LIVE_NASA_POWER("Live NASA POWER API (MERRA-2 / CERES)"),
    CACHED_NASA_REFERENCE("Cached Verified NASA Reference Record"),
    OFFLINE_CACHE_FALLBACK("Offline Fallback — Cached NASA Record"),
}

@Immutable
data class InvestigationConfig(
    val id: String,
    val title: String,
    val region: StudyRegion,
    val variableIds: List<String>,
    val startYear: Int,
    val endYear: Int,
    val aggregation: AggregationMode = AggregationMode.DESEASONALIZED_ANOMALY,
    val trendMethod: TrendMethod = TrendMethod.SEASONAL_MANN_KENDALL,
    val slopeMethod: SlopeMethod = SlopeMethod.SENS_SLOPE,
    val significanceAlpha: Double = 0.05,
    val dataSourceMode: DataSourceMode = DataSourceMode.AUTO_LIVE_WITH_CACHE,
    val comparisonRegion: StudyRegion? = null,
)

enum class TrendDirection(val label: String) {
    INCREASING("Increasing"),
    DECREASING("Decreasing"),
    NO_CLEAR_DIRECTION("No clear direction"),
}

enum class AnalysisStatus {
    SUCCESS,
    INSUFFICIENT_DATA,
    NO_DATA,
    ERROR,
}

@Immutable
data class TrendLineEndpoint(
    val startYear: Double,
    val startValue: Double,
    val endYear: Double,
    val endValue: Double,
)

@Immutable
data class VariableTrendResult(
    val variable: VariableDefinition,
    val dataset: DatasetMetadata,
    val region: StudyRegion,
    val status: AnalysisStatus,
    val statusMessage: String,
    val trendDirection: TrendDirection,
    val slopePerDecade: Double?,
    val slopePerYear: Double?,
    val slopeUnit: String,
    val interceptAtStart: Double?,
    val rSquared: Double?,
    val ci95LowPerDecade: Double?,
    val ci95HighPerDecade: Double?,
    val pValue: Double?,
    val significanceThreshold: Double,
    val isStatisticallySignificant: Boolean,
    val zScore: Double?,
    val kendallTau: Double?,
    val lag1Autocorrelation: Double?,
    val hasAutocorrelationCaveat: Boolean,
    val nValidObservations: Int,
    val nTotalSlots: Int,
    val nMissingOrFlagged: Int,
    val missingFraction: Double,
    val analysisPeriodStart: String,
    val analysisPeriodEnd: String,
    val lastObservationDate: String,
    val methodName: String,
    val methodParameters: String,
    val slopeEstimatorName: String,
    val aggregationName: String,
    val seasonalityHandling: String,
    val autocorrelationHandling: String,
    val dataSourceId: String,
    val productVersion: String,
    val processingVersion: String,
    val dataProvenanceStatus: DataProvenanceStatus,
    val retrievedAtIso: String,
    val series: List<TimeSeriesPoint>,
    val trendLine: TrendLineEndpoint?,
    val neutralTitle: String,
    val interpretationText: String,
    val whatThisMeans: String,
    val whatThisDoesNotMean: String,
    val limitations: List<String>,
    val chartTextSummary: String,
)

@Immutable
data class ComparisonCorrelation(
    val labelA: String,
    val labelB: String,
    val pearsonR: Double?,
    val overlapCount: Int,
    val caveat: String,
)

@Immutable
data class InvestigationResult(
    val config: InvestigationConfig,
    val primaryResult: VariableTrendResult,
    val secondaryResult: VariableTrendResult? = null,
    val comparisonRegionResult: VariableTrendResult? = null,
    val comparisonCorrelation: ComparisonCorrelation? = null,
    val computedAtIso: String,
)

@Immutable
data class SavedDiscoverySummary(
    val id: String,
    val title: String,
    val regionName: String,
    val primaryVariableName: String,
    val variableIds: List<String>,
    val periodLabel: String,
    val direction: TrendDirection,
    val isSignificant: Boolean,
    val slopeFormatted: String,
    val pValueFormatted: String,
    val savedAtIso: String,
    val isPreloadedReference: Boolean,
    val resultJson: String,
)
