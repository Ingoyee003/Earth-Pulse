package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiExplanationResponse
import com.example.data.ai.AiRepository
import com.example.data.ai.AiVariableSuggestion
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.catalog.NasaCatalog
import com.example.data.local.EarthPulseDatabase
import com.example.data.model.AggregationMode
import com.example.data.model.AnalysisStatus
import com.example.data.model.DataSourceMode
import com.example.data.model.FeaturedInvestigation
import com.example.data.model.InvestigationConfig
import com.example.data.model.InvestigationResult
import com.example.data.model.SavedDiscoverySummary
import com.example.data.model.SlopeMethod
import com.example.data.model.StudyRegion
import com.example.data.model.TrendDirection
import com.example.data.model.TrendMethod
import com.example.data.repository.InvestigationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data object Loading : AnalysisUiState
    data class Success(val result: InvestigationResult) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

data class InvestigationFormState(
    val currentStep: Int = 0, // 0: Location, 1: Variables, 2: Time period, 3: Review
    val locationQuery: String = "",
    val selectedRegion: StudyRegion? = NasaCatalog.regions.first(),
    val customLatInput: String = "",
    val customLonInput: String = "",
    val selectedVariableIds: List<String> = listOf("T2M"),
    val startYear: Int = 2000,
    val endYear: Int = 2024,
    val aggregation: AggregationMode = AggregationMode.DESEASONALIZED_ANOMALY,
    val trendMethod: TrendMethod = TrendMethod.SEASONAL_MANN_KENDALL,
    val slopeMethod: SlopeMethod = SlopeMethod.SENS_SLOPE,
    val significanceAlpha: Double = 0.05,
    val dataSourceMode: DataSourceMode = DataSourceMode.AUTO_LIVE_WITH_CACHE,
    val comparisonRegion: StudyRegion? = null,
    val advancedExpanded: Boolean = false,
) {
    val availableMinYear: Int
        get() = selectedVariableIds.maxOfOrNull { NasaCatalog.variableFor(it).availableStartYear } ?: 1981

    val availableMaxYear: Int
        get() = selectedVariableIds.minOfOrNull { NasaCatalog.variableFor(it).availableEndYear } ?: 2025

    val validationMessage: String?
        get() = when {
            selectedRegion == null -> "Select a study region or tap a location on the map."
            selectedVariableIds.isEmpty() -> "Select 1 or 2 NASA variables to analyze."
            startYear < availableMinYear || endYear > availableMaxYear ->
                "Selected dates must fall within ${availableMinYear}–${availableMaxYear} for the chosen variable(s)."
            endYear - startYear < 1 -> "Select at least a 2-year window so monthly trends have sufficient coverage."
            else -> null
        }

    val isValid: Boolean get() = validationMessage == null
}

data class DiscoveriesFilterState(
    val regionQuery: String = "",
    val variableFilterId: String? = null,
    val directionFilter: TrendDirection? = null,
    val significantOnly: Boolean = false,
)

class EarthPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = EarthPulseDatabase.getInstance(application)
    private val repository = InvestigationRepository(application, database.savedInvestigationDao())
    private val aiRepository: AiRepository = PlaceholderAiRepository()

    private val _formState = MutableStateFlow(InvestigationFormState())
    val formState: StateFlow<InvestigationFormState> = _formState.asStateFlow()

    private val _analysisState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysisState: StateFlow<AnalysisUiState> = _analysisState.asStateFlow()

    private val _lastAnalyzedConfig = MutableStateFlow<InvestigationConfig?>(null)

    private val _aiExplanation = MutableStateFlow<AiExplanationResponse?>(null)
    val aiExplanation: StateFlow<AiExplanationResponse?> = _aiExplanation.asStateFlow()

    private val _aiVariableSuggestion = MutableStateFlow<AiVariableSuggestion?>(null)
    val aiVariableSuggestion: StateFlow<AiVariableSuggestion?> = _aiVariableSuggestion.asStateFlow()

    private val _filterState = MutableStateFlow(DiscoveriesFilterState())
    val filterState: StateFlow<DiscoveriesFilterState> = _filterState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _pendingDiscardConfirmation = MutableStateFlow(false)
    val pendingDiscardConfirmation: StateFlow<Boolean> = _pendingDiscardConfirmation.asStateFlow()

    val savedDiscoveries: StateFlow<List<SavedDiscoverySummary>> = repository
        .observeSavedDiscoveries()
        .combine(_filterState) { list, filter ->
            list.filter { item ->
                val matchesRegion = filter.regionQuery.isBlank() ||
                    item.regionName.contains(filter.regionQuery, ignoreCase = true) ||
                    item.title.contains(filter.regionQuery, ignoreCase = true)
                val matchesVar = filter.variableFilterId == null ||
                    item.variableIds.contains(filter.variableFilterId)
                val matchesDir = filter.directionFilter == null ||
                    item.direction == filter.directionFilter
                val matchesSig = !filter.significantOnly || item.isSignificant
                matchesRegion && matchesVar && matchesDir && matchesSig
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun setStep(step: Int) {
        _formState.update { it.copy(currentStep = step.coerceIn(0, 3)) }
    }

    fun updateLocationQuery(query: String) {
        _formState.update { it.copy(locationQuery = query) }
    }

    fun selectRegion(region: StudyRegion) {
        _formState.update { state ->
            val updatedComparison = if (state.comparisonRegion?.id == region.id) null else state.comparisonRegion
            state.copy(
                selectedRegion = region,
                comparisonRegion = updatedComparison,
                customLatInput = String.format(java.util.Locale.US, "%.2f", region.centroidLat),
                customLonInput = String.format(java.util.Locale.US, "%.2f", region.centroidLon),
            )
        }
    }

    fun selectCustomCoordinates(lat: Double, lon: Double) {
        val custom = NasaCatalog.createCustomRegion(lat, lon)
        _formState.update { state ->
            // Filter to variables with live NASA POWER parameter bindings
            val validPowerVars = state.selectedVariableIds.filter { NasaCatalog.variableFor(it).nasaPowerParam != null }
                .ifEmpty { listOf("T2M") }
            val minY = validPowerVars.maxOf { NasaCatalog.variableFor(it).availableStartYear }
            val maxY = validPowerVars.minOf { NasaCatalog.variableFor(it).availableEndYear }
            state.copy(
                selectedRegion = custom,
                selectedVariableIds = validPowerVars,
                startYear = state.startYear.coerceIn(minY, maxY - 1),
                endYear = state.endYear.coerceIn(minY + 1, maxY),
                customLatInput = String.format(java.util.Locale.US, "%.2f", custom.centroidLat),
                customLonInput = String.format(java.util.Locale.US, "%.2f", custom.centroidLon),
            )
        }
        showToast("Selected custom point (${custom.name}) for live NASA POWER query.")
    }

    fun applyCustomLatLonInputs(latText: String, lonText: String) {
        _formState.update { it.copy(customLatInput = latText, customLonInput = lonText) }
        val lat = latText.toDoubleOrNull()
        val lon = lonText.toDoubleOrNull()
        if (lat != null && lon != null && lat in -89.5..89.5 && lon in -179.5..179.5) {
            selectCustomCoordinates(lat, lon)
        } else {
            showToast("Enter valid latitude (-89.5 to 89.5) and longitude (-179.5 to 179.5).")
        }
    }

    fun toggleVariable(variableId: String) {
        _formState.update { state ->
            val current = state.selectedVariableIds.toMutableList()
            if (current.contains(variableId)) {
                if (current.size > 1) current.remove(variableId)
                else return@update state
            } else {
                if (current.size >= 2) {
                    current.removeAt(0)
                }
                current.add(variableId)
            }
            val minY = current.maxOf { NasaCatalog.variableFor(it).availableStartYear }
            val maxY = current.minOf { NasaCatalog.variableFor(it).availableEndYear }
            val clampedStart = state.startYear.coerceIn(minY, maxY - 1)
            val clampedEnd = state.endYear.coerceIn(clampedStart + 1, maxY)
            state.copy(
                selectedVariableIds = current,
                startYear = clampedStart,
                endYear = clampedEnd,
            )
        }
    }

    fun updateDateRange(startYear: Int, endYear: Int) {
        _formState.update { state ->
            val minY = state.availableMinYear
            val maxY = state.availableMaxYear
            val s = startYear.coerceIn(minY, maxY - 1)
            val e = endYear.coerceIn(s + 1, maxY)
            state.copy(startYear = s, endYear = e)
        }
    }

    fun updateAggregation(mode: AggregationMode) {
        _formState.update { state ->
            val adjustedMethod = if (mode == AggregationMode.ANNUAL_MEAN) {
                TrendMethod.MANN_KENDALL
            } else {
                state.trendMethod
            }
            state.copy(aggregation = mode, trendMethod = adjustedMethod)
        }
    }

    fun updateTrendMethod(method: TrendMethod) {
        _formState.update { it.copy(trendMethod = method) }
    }

    fun updateSlopeMethod(method: SlopeMethod) {
        _formState.update { it.copy(slopeMethod = method) }
    }

    fun updateAlpha(alpha: Double) {
        _formState.update { it.copy(significanceAlpha = alpha) }
    }

    fun updateDataSourceMode(mode: DataSourceMode) {
        _formState.update { it.copy(dataSourceMode = mode) }
    }

    fun updateComparisonRegion(region: StudyRegion?) {
        _formState.update { it.copy(comparisonRegion = region) }
    }

    fun toggleAdvancedSettings() {
        _formState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    fun askAiForVariableSuggestion(question: String) {
        if (question.isBlank()) {
            showToast("Describe what environmental change you want to investigate.")
            return
        }
        viewModelScope.launch {
            val suggestion = aiRepository.suggestVariables(question)
            _aiVariableSuggestion.value = suggestion
        }
    }

    fun applySuggestedVariables(variableIds: List<String>) {
        if (variableIds.isEmpty()) return
        _formState.update { state ->
            val filtered = variableIds.take(2)
            val minY = filtered.maxOf { NasaCatalog.variableFor(it).availableStartYear }
            val maxY = filtered.minOf { NasaCatalog.variableFor(it).availableEndYear }
            state.copy(
                selectedVariableIds = filtered,
                startYear = state.startYear.coerceIn(minY, maxY - 1),
                endYear = state.endYear.coerceIn(minY + 1, maxY),
            )
        }
        showToast("Applied suggested NASA variables.")
    }

    fun hasUnsavedSettingsDifference(): Boolean {
        val last = _lastAnalyzedConfig.value ?: return false
        val current = _formState.value
        return last.region.id != current.selectedRegion?.id ||
            last.variableIds != current.selectedVariableIds ||
            last.startYear != current.startYear ||
            last.endYear != current.endYear ||
            last.aggregation != current.aggregation ||
            last.trendMethod != current.trendMethod ||
            last.slopeMethod != current.slopeMethod ||
            last.significanceAlpha != current.significanceAlpha
    }

    fun requestAnalysis(forceDiscardPrevious: Boolean = false, onNavigateToResults: () -> Unit) {
        val form = _formState.value
        val validation = form.validationMessage
        if (validation != null) {
            showToast(validation)
            return
        }
        if (!forceDiscardPrevious && _analysisState.value is AnalysisUiState.Success && hasUnsavedSettingsDifference()) {
            _pendingDiscardConfirmation.value = true
            return
        }
        _pendingDiscardConfirmation.value = false
        val region = form.selectedRegion!!
        val config = InvestigationConfig(
            id = UUID.randomUUID().toString(),
            title = "${NasaCatalog.variableFor(form.selectedVariableIds.first()).shortLabel} in ${region.name}",
            region = region,
            variableIds = form.selectedVariableIds,
            startYear = form.startYear,
            endYear = form.endYear,
            aggregation = form.aggregation,
            trendMethod = form.trendMethod,
            slopeMethod = form.slopeMethod,
            significanceAlpha = form.significanceAlpha,
            dataSourceMode = form.dataSourceMode,
            comparisonRegion = form.comparisonRegion,
        )
        executeConfig(config, onNavigateToResults)
    }

    fun dismissDiscardConfirmation() {
        _pendingDiscardConfirmation.value = false
    }

    fun launchFeaturedDemo(demo: FeaturedInvestigation, onNavigateToResults: () -> Unit) {
        val region = NasaCatalog.regionFor(demo.regionId)
        _formState.value = InvestigationFormState(
            currentStep = 3,
            selectedRegion = region,
            selectedVariableIds = demo.variableIds,
            startYear = demo.startYear,
            endYear = demo.endYear,
            aggregation = demo.aggregation,
            trendMethod = demo.trendMethod,
            slopeMethod = SlopeMethod.SENS_SLOPE,
            significanceAlpha = 0.05,
            dataSourceMode = DataSourceMode.AUTO_LIVE_WITH_CACHE,
        )
        val config = InvestigationConfig(
            id = "demo_${demo.id}",
            title = demo.title,
            region = region,
            variableIds = demo.variableIds,
            startYear = demo.startYear,
            endYear = demo.endYear,
            aggregation = demo.aggregation,
            trendMethod = demo.trendMethod,
            slopeMethod = SlopeMethod.SENS_SLOPE,
            significanceAlpha = 0.05,
            dataSourceMode = DataSourceMode.AUTO_LIVE_WITH_CACHE,
        )
        executeConfig(config, onNavigateToResults)
    }

    fun openSavedDiscovery(summary: SavedDiscoverySummary, onNavigateToResults: () -> Unit) {
        val parsed = repository.buildConfigFromSavedSummary(summary)
        if (parsed == null) {
            showToast("Could not restore saved investigation configuration.")
            return
        }
        _formState.value = InvestigationFormState(
            currentStep = 3,
            selectedRegion = parsed.region,
            selectedVariableIds = parsed.variableIds,
            startYear = parsed.startYear,
            endYear = parsed.endYear,
        )
        executeConfig(parsed, onNavigateToResults)
    }

    fun runRegionComparison(secondRegion: StudyRegion) {
        val currentResult = (_analysisState.value as? AnalysisUiState.Success)?.result ?: return
        val updatedConfig = currentResult.config.copy(comparisonRegion = secondRegion)
        _formState.update { it.copy(comparisonRegion = secondRegion) }
        executeConfig(updatedConfig) {}
    }

    private fun executeConfig(config: InvestigationConfig, onNavigateToResults: () -> Unit) {
        _analysisState.value = AnalysisUiState.Loading
        _aiExplanation.value = null
        onNavigateToResults()
        viewModelScope.launch {
            try {
                val result = repository.runInvestigation(config)
                _lastAnalyzedConfig.value = config
                if (result.primaryResult.status == AnalysisStatus.ERROR) {
                    _analysisState.value = AnalysisUiState.Error(result.primaryResult.statusMessage)
                } else {
                    _analysisState.value = AnalysisUiState.Success(result)
                    _aiExplanation.value = aiRepository.explainResult(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _analysisState.value = AnalysisUiState.Error(
                    e.localizedMessage ?: "Trend analysis failed unexpectedly.",
                )
            }
        }
    }

    fun saveCurrentResult() {
        val current = (_analysisState.value as? AnalysisUiState.Success)?.result ?: return
        viewModelScope.launch {
            repository.saveInvestigation(current)
            showToast("Investigation saved locally on this device.")
        }
    }

    fun deleteDiscovery(id: String) {
        viewModelScope.launch {
            repository.deleteSavedDiscovery(id)
            showToast("Removed saved discovery.")
        }
    }

    fun buildExportCsv(): String? {
        val current = (_analysisState.value as? AnalysisUiState.Success)?.result ?: return null
        return repository.exportCsv(current)
    }

    fun buildExportJson(): String? {
        val current = (_analysisState.value as? AnalysisUiState.Success)?.result ?: return null
        return repository.exportJson(current)
    }

    fun updateDiscoveryFilter(
        regionQuery: String = _filterState.value.regionQuery,
        variableFilterId: String? = _filterState.value.variableFilterId,
        directionFilter: TrendDirection? = _filterState.value.directionFilter,
        significantOnly: Boolean = _filterState.value.significantOnly,
    ) {
        _filterState.value = DiscoveriesFilterState(
            regionQuery = regionQuery,
            variableFilterId = variableFilterId,
            directionFilter = directionFilter,
            significantOnly = significantOnly,
        )
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
