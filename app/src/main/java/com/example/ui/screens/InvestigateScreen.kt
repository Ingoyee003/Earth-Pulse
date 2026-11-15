package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiVariableSuggestion
import com.example.data.catalog.NasaCatalog
import com.example.data.model.AggregationMode
import com.example.data.model.DataSourceMode
import com.example.data.model.SlopeMethod
import com.example.data.model.StudyRegion
import com.example.data.model.TrendMethod
import com.example.ui.components.AppHeader
import com.example.ui.components.DatasetBadge
import com.example.ui.components.DateRangePicker
import com.example.ui.components.PrimaryButton
import com.example.ui.components.RegionMap
import com.example.ui.components.SearchLocation
import com.example.ui.components.SecondaryButton
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.Stepper
import com.example.ui.components.VariableCard
import com.example.ui.theme.AmberContainer
import com.example.ui.viewmodel.InvestigationFormState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvestigateScreen(
    formState: InvestigationFormState,
    aiSuggestion: AiVariableSuggestion?,
    showDiscardDialog: Boolean,
    onStepSelected: (Int) -> Unit,
    onLocationQueryChange: (String) -> Unit,
    onSelectRegion: (StudyRegion) -> Unit,
    onSelectCustomPoint: (Double, Double) -> Unit,
    onApplyCustomInputs: (String, String) -> Unit,
    onToggleVariable: (String) -> Unit,
    onAskAiSuggestion: (String) -> Unit,
    onApplyAiSuggestion: (List<String>) -> Unit,
    onDateRangeChange: (Int, Int) -> Unit,
    onToggleAdvanced: () -> Unit,
    onAggregationChange: (AggregationMode) -> Unit,
    onTrendMethodChange: (TrendMethod) -> Unit,
    onSlopeMethodChange: (SlopeMethod) -> Unit,
    onAlphaChange: (Double) -> Unit,
    onDataSourceModeChange: (DataSourceMode) -> Unit,
    onAnalyseClick: () -> Unit,
    onConfirmDiscardAndAnalyse: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onOpenAboutData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscardDialog,
            title = { Text("Replace previous investigation?") },
            text = {
                Text("Your settings changed since the last completed analysis. Running a new analysis will replace the active results workspace (saved discoveries remain in your library).")
            },
            confirmButton = {
                TextButton(onClick = onConfirmDiscardAndAnalyse) {
                    Text("Run new analysis")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscardDialog) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppHeader(
            title = "INVESTIGATE",
            subtitle = "Configure study region, NASA variables & statistical window",
            onHelpClick = onOpenAboutData,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Stepper(
                        steps = listOf("Location", "Variables", "Period", "Review"),
                        currentStep = formState.currentStep,
                        onStepSelected = onStepSelected,
                    )
                }

                when (formState.currentStep) {
                    0 -> {
                        item {
                            LocationStepContent(
                                formState = formState,
                                onLocationQueryChange = onLocationQueryChange,
                                onSelectRegion = onSelectRegion,
                                onSelectCustomPoint = onSelectCustomPoint,
                                onApplyCustomInputs = onApplyCustomInputs,
                                onNextStep = { onStepSelected(1) },
                            )
                        }
                    }

                    1 -> {
                        item {
                            VariablesStepContent(
                                formState = formState,
                                aiSuggestion = aiSuggestion,
                                onToggleVariable = onToggleVariable,
                                onAskAiSuggestion = onAskAiSuggestion,
                                onApplyAiSuggestion = onApplyAiSuggestion,
                                onBackStep = { onStepSelected(0) },
                                onNextStep = { onStepSelected(2) },
                            )
                        }
                    }

                    2 -> {
                        item {
                            TimePeriodStepContent(
                                formState = formState,
                                onDateRangeChange = onDateRangeChange,
                                onToggleAdvanced = onToggleAdvanced,
                                onAggregationChange = onAggregationChange,
                                onTrendMethodChange = onTrendMethodChange,
                                onSlopeMethodChange = onSlopeMethodChange,
                                onAlphaChange = onAlphaChange,
                                onDataSourceModeChange = onDataSourceModeChange,
                                onBackStep = { onStepSelected(1) },
                                onNextStep = { onStepSelected(3) },
                            )
                        }
                    }

                    else -> {
                        item {
                            ReviewStepContent(
                                formState = formState,
                                onStepSelected = onStepSelected,
                                onAnalyseClick = onAnalyseClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationStepContent(
    formState: InvestigationFormState,
    onLocationQueryChange: (String) -> Unit,
    onSelectRegion: (StudyRegion) -> Unit,
    onSelectCustomPoint: (Double, Double) -> Unit,
    onApplyCustomInputs: (String, String) -> Unit,
    onNextStep: () -> Unit,
) {
    val filteredRegions = remember(formState.locationQuery) {
        val q = formState.locationQuery.trim()
        if (q.isEmpty()) NasaCatalog.regions
        else NasaCatalog.regions.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.countryOrArea.contains(q, ignoreCase = true) ||
                it.regionType.contains(q, ignoreCase = true)
        }
    }
    var latText by remember(formState.customLatInput) { mutableStateOf(formState.customLatInput) }
    var lonText by remember(formState.customLonInput) { mutableStateOf(formState.customLonInput) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchLocation(
            query = formState.locationQuery,
            onQueryChange = onLocationQueryChange,
        )

        RegionMap(
            regions = NasaCatalog.regions,
            selectedRegion = formState.selectedRegion,
            onSelectRegion = onSelectRegion,
            onSelectCustomPoint = onSelectCustomPoint,
        )

        // Custom Coordinate Entry for Live Global NASA POWER API
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Or Query Custom Global Coordinates (Live NASA POWER)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Set",
                        onClick = { onApplyCustomInputs(latText, lonText) },
                        testTag = "apply_custom_coords_button",
                    )
                }
            }
        }

        Text(
            text = "Curated NASA Study Regions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        filteredRegions.forEach { region ->
            val selected = formState.selectedRegion?.id == region.id
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectRegion(region) }
                    .testTag("region_card_${region.id}"),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = region.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (selected) {
                            StatusBadge(label = "Selected", tone = StatusTone.SIGNIFICANT)
                        }
                    }
                    Text(
                        text = region.summaryDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%s • Area ~%,d km² • Centroid %.2f°, %.2f°",
                            region.countryOrArea,
                            region.areaKm2,
                            region.centroidLat,
                            region.centroidLon,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        PrimaryButton(
            text = "Continue to Variables",
            onClick = onNextStep,
            enabled = formState.selectedRegion != null,
            modifier = Modifier.fillMaxWidth(),
            testTag = "step1_next_button",
        )
    }
}

@Composable
private fun VariablesStepContent(
    formState: InvestigationFormState,
    aiSuggestion: AiVariableSuggestion?,
    onToggleVariable: (String) -> Unit,
    onAskAiSuggestion: (String) -> Unit,
    onApplyAiSuggestion: (List<String>) -> Unit,
    onBackStep: () -> Unit,
    onNextStep: () -> Unit,
) {
    var questionText by remember { mutableStateOf("") }
    val isCustomPoint = formState.selectedRegion?.isCustomPoint == true

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Select 1 or 2 NASA Earth-Science Variables",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Selecting 2 variables unlocks side-by-side comparison and concurrent correlation checks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Optional AI Variable Helper (Placeholder Mode notice per Firebase decline contract)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Variable Advisor (Optional Helper)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "AI features require Firebase AI Logic. Currently running in deterministic placeholder preview mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        placeholder = { Text("e.g., Is vegetation greenness shifting with heat?") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Suggest",
                        onClick = { onAskAiSuggestion(questionText) },
                        testTag = "ai_suggest_variables_button",
                    )
                }
                if (aiSuggestion != null) {
                    Text(
                        text = aiSuggestion.rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onApplyAiSuggestion(aiSuggestion.suggestedVariableIds) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Apply suggested variables")
                    }
                }
            }
        }

        val displayVars = NasaCatalog.variables

        if (isCustomPoint) {
            Text(
                text = "Custom coordinates query the live NASA POWER API directly across all 6 Earth-system variables below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        displayVars.forEach { variable ->
            val dataset = NasaCatalog.datasetFor(variable.datasetId)
            VariableCard(
                variable = variable,
                datasetShortName = dataset.shortName,
                isSelected = formState.selectedVariableIds.contains(variable.id),
                onToggle = { onToggleVariable(variable.id) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(
                text = "Back",
                onClick = onBackStep,
                modifier = Modifier.weight(1f),
                testTag = "step2_back_button",
            )
            PrimaryButton(
                text = "Continue to Period",
                onClick = onNextStep,
                enabled = formState.selectedVariableIds.isNotEmpty(),
                modifier = Modifier.weight(2f),
                testTag = "step2_next_button",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimePeriodStepContent(
    formState: InvestigationFormState,
    onDateRangeChange: (Int, Int) -> Unit,
    onToggleAdvanced: () -> Unit,
    onAggregationChange: (AggregationMode) -> Unit,
    onTrendMethodChange: (TrendMethod) -> Unit,
    onSlopeMethodChange: (SlopeMethod) -> Unit,
    onAlphaChange: (Double) -> Unit,
    onDataSourceModeChange: (DataSourceMode) -> Unit,
    onBackStep: () -> Unit,
    onNextStep: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DateRangePicker(
            startYear = formState.startYear,
            endYear = formState.endYear,
            minAvailableYear = formState.availableMinYear,
            maxAvailableYear = formState.availableMaxYear,
            onRangeChange = onDateRangeChange,
        )

        // Collapsed-by-default Advanced Analysis Settings (Spec Section 05 & 07.B)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleAdvanced)
                        .testTag("toggle_advanced_settings"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(
                                text = "Advanced Analysis Settings",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${formState.aggregation.label} • ${formState.trendMethod.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (formState.advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (formState.advancedExpanded) "Collapse settings" else "Expand settings",
                    )
                }

                AnimatedVisibility(visible = formState.advancedExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        Text("Temporal Aggregation", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AggregationMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = formState.aggregation == mode,
                                    onClick = { onAggregationChange(mode) },
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                        Text(
                            text = formState.aggregation.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text("Significance Test", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TrendMethod.entries.forEach { method ->
                                val disabled = method == TrendMethod.SEASONAL_MANN_KENDALL &&
                                    formState.aggregation == AggregationMode.ANNUAL_MEAN
                                FilterChip(
                                    selected = formState.trendMethod == method,
                                    onClick = { if (!disabled) onTrendMethodChange(method) },
                                    enabled = !disabled,
                                    label = { Text(method.label) },
                                )
                            }
                        }

                        Text("Slope Estimator", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SlopeMethod.entries.forEach { sm ->
                                FilterChip(
                                    selected = formState.slopeMethod == sm,
                                    onClick = { onSlopeMethodChange(sm) },
                                    label = { Text(sm.label) },
                                )
                            }
                        }

                        Text("Significance Threshold (α)", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.05, 0.01).forEach { alpha ->
                                FilterChip(
                                    selected = formState.significanceAlpha == alpha,
                                    onClick = { onAlphaChange(alpha) },
                                    label = { Text("α = $alpha") },
                                )
                            }
                        }

                        Text("Data Retrieval Mode", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DataSourceMode.entries.forEach { dsMode ->
                                FilterChip(
                                    selected = formState.dataSourceMode == dsMode,
                                    onClick = { onDataSourceModeChange(dsMode) },
                                    label = { Text(dsMode.label) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(
                text = "Back",
                onClick = onBackStep,
                modifier = Modifier.weight(1f),
                testTag = "step3_back_button",
            )
            PrimaryButton(
                text = "Review Setup",
                onClick = onNextStep,
                modifier = Modifier.weight(2f),
                testTag = "step3_next_button",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewStepContent(
    formState: InvestigationFormState,
    onStepSelected: (Int) -> Unit,
    onAnalyseClick: () -> Unit,
) {
    val selectedVars = formState.selectedVariableIds.map { NasaCatalog.variableFor(it) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Investigation Summary",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ReviewLine(
                    label = "Study Region",
                    value = formState.selectedRegion?.name ?: "None selected",
                    onEdit = { onStepSelected(0) },
                )
                ReviewLine(
                    label = "NASA Variable(s)",
                    value = selectedVars.joinToString(" + ") { "${it.displayName} (${it.unit})" },
                    onEdit = { onStepSelected(1) },
                )
                ReviewLine(
                    label = "Analysis Period",
                    value = "${formState.startYear}–${formState.endYear}",
                    onEdit = { onStepSelected(2) },
                )
                ReviewLine(
                    label = "Methodology",
                    value = "${formState.aggregation.label} • ${formState.trendMethod.label} • ${formState.slopeMethod.label} (α=${formState.significanceAlpha})",
                    onEdit = { onStepSelected(2) },
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedVars.forEach { v ->
                        DatasetBadge(text = NasaCatalog.datasetFor(v.datasetId).shortName)
                    }
                    DatasetBadge(text = formState.dataSourceMode.label)
                }
            }
        }

        if (formState.validationMessage != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AmberContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formState.validationMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        PrimaryButton(
            text = "Analyse trends",
            onClick = onAnalyseClick,
            enabled = formState.isValid,
            icon = Icons.Default.Analytics,
            modifier = Modifier.fillMaxWidth(),
            testTag = "analyse_trends_button",
        )
    }
}

@Composable
private fun ReviewLine(
    label: String,
    value: String,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(onClick = onEdit) {
            Text("Edit")
        }
    }
}
