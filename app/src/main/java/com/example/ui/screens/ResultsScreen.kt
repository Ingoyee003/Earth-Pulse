package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiExplanationResponse
import com.example.data.catalog.NasaCatalog
import com.example.data.model.AnalysisStatus
import com.example.data.model.DataProvenanceStatus
import com.example.data.model.InvestigationResult
import com.example.data.model.StudyRegion
import com.example.data.model.VariableTrendResult
import com.example.domain.analysis.TrendAnalyzer
import com.example.ui.components.AppHeader
import com.example.ui.components.ComparisonChart
import com.example.ui.components.DatasetBadge
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.EvidenceDrawer
import com.example.ui.components.LoadingSkeleton
import com.example.ui.components.MetricCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.RegionMap
import com.example.ui.components.SecondaryButton
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.TrendChart
import com.example.ui.components.directionIconFor
import com.example.ui.theme.AmberContainer
import com.example.ui.viewmodel.AnalysisUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultsScreen(
    analysisState: AnalysisUiState,
    aiExplanation: AiExplanationResponse?,
    onBackToInvestigate: () -> Unit,
    onSaveResult: () -> Unit,
    onCompareRegion: (StudyRegion) -> Unit,
    onBuildExportCsv: () -> String?,
    onBuildExportJson: () -> String?,
    onNotifyToast: (String) -> Unit,
    onOpenAboutData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackToInvestigate)

    var showEvidenceFor by remember { mutableStateOf<VariableTrendResult?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showCompareRegionDialog by remember { mutableStateOf(false) }
    var pendingSaveContent by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val content = pendingSaveContent
        pendingSaveContent = null
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onNotifyToast("Exported NASA trend report successfully.")
            }.onFailure {
                onNotifyToast("Failed to save exported report.")
            }
        }
    }

    if (showEvidenceFor != null) {
        EvidenceDrawer(
            result = showEvidenceFor!!,
            onDismiss = { showEvidenceFor = null },
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Reproducible Report") },
            text = {
                Text("Choose CSV for raw/QC time series with provenance header, or JSON for complete statistical outputs and metadata.")
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            val csv = onBuildExportCsv()
                            if (csv != null) {
                                pendingSaveContent = csv
                                createDocLauncher.launch("earth_pulse_trend.csv")
                            }
                        },
                    ) { Text("Save CSV") }
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            val json = onBuildExportJson()
                            if (json != null) {
                                pendingSaveContent = json
                                createDocLauncher.launch("earth_pulse_report.json")
                            }
                        },
                    ) { Text("Save JSON") }
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            val csv = onBuildExportCsv() ?: return@TextButton
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "EARTH PULSE NASA Trend Report")
                                putExtra(Intent.EXTRA_TEXT, csv.take(8000))
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share EARTH PULSE Report"))
                        },
                    ) { Text("Share") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showCompareRegionDialog && analysisState is AnalysisUiState.Success) {
        val currentRegionId = analysisState.result.primaryResult.region.id
        AlertDialog(
            onDismissRequest = { showCompareRegionDialog = false },
            title = { Text("Compare With Second Region") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Run ${analysisState.result.primaryResult.variable.displayName} (${analysisState.result.config.startYear}–${analysisState.result.config.endYear}) using the same statistical method on another curated study region:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    NasaCatalog.regions.filter { it.id != currentRegionId }.forEach { candidate ->
                        TextButton(
                            onClick = {
                                showCompareRegionDialog = false
                                onCompareRegion(candidate)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${candidate.name} (${candidate.countryOrArea})")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCompareRegionDialog = false }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppHeader(
            title = "RESULTS WORKSPACE",
            subtitle = "Deterministic NASA trend estimates & provenance",
            onHelpClick = onOpenAboutData,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (analysisState) {
                is AnalysisUiState.Idle -> {
                    Box(modifier = Modifier.padding(16.dp).widthIn(max = 760.dp)) {
                        EmptyState(
                            title = "No investigation active",
                            message = "Select a study region, NASA variables, and analysis period to inspect deterministic trends.",
                            actionLabel = "Configure investigation",
                            onAction = onBackToInvestigate,
                        )
                    }
                }

                is AnalysisUiState.Loading -> {
                    Box(modifier = Modifier.widthIn(max = 760.dp)) {
                        LoadingSkeleton()
                    }
                }

                is AnalysisUiState.Error -> {
                    Box(modifier = Modifier.padding(16.dp).widthIn(max = 760.dp)) {
                        ErrorState(
                            title = "Analysis Could Not Be Completed",
                            message = analysisState.message,
                            retryLabel = "Change analysis settings",
                            onRetry = onBackToInvestigate,
                        )
                    }
                }

                is AnalysisUiState.Success -> {
                    val result = analysisState.result
                    val primary = result.primaryResult

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Top Summary Banner
                        item {
                            ResultTopSummaryCard(
                                result = result,
                                onOpenEvidence = { showEvidenceFor = primary },
                            )
                        }

                        // Insufficient Data / Recovery Path Card
                        if (primary.status != AnalysisStatus.SUCCESS) {
                            item {
                                ErrorState(
                                    title = "Insufficient Observations for Reliable Trend",
                                    message = primary.statusMessage,
                                    retryLabel = "Adjust date range or region",
                                    onRetry = onBackToInvestigate,
                                )
                            }
                            item {
                                TrendChart(result = primary)
                            }
                        } else {
                            // Neutral Finding Headline
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = primary.neutralTitle,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            DatasetBadge(text = primary.dataset.shortName)
                                            DatasetBadge(text = primary.productVersion)
                                            DatasetBadge(text = "${primary.analysisPeriodStart} to ${primary.analysisPeriodEnd}")
                                        }
                                    }
                                }
                            }

                            // 4 Metric Cards (2x2 grid)
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        val slopeText = primary.slopePerDecade?.let {
                                            String.format(Locale.US, "%+.3f", it)
                                        } ?: "N/A"
                                        val ciUnit = if (primary.ci95LowPerDecade != null && primary.ci95HighPerDecade != null) {
                                            String.format(
                                                Locale.US,
                                                "%s (95%% CI: %+.2f..%+.2f)",
                                                primary.slopeUnit,
                                                primary.ci95LowPerDecade,
                                                primary.ci95HighPerDecade,
                                            )
                                        } else {
                                            primary.slopeUnit
                                        }

                                        MetricCard(
                                            label = "Estimated Rate (${primary.slopeEstimatorName})",
                                            value = slopeText,
                                            unit = ciUnit,
                                            statusLabel = primary.trendDirection.label,
                                            statusTone = if (primary.isStatisticallySignificant) StatusTone.SIGNIFICANT else StatusTone.NOT_SIGNIFICANT,
                                            tooltipExplanation = "Rate of change per decade estimated using ${primary.slopeEstimatorName}. Positive values indicate an upward slope over ${result.config.startYear}–${result.config.endYear}.",
                                            directionIcon = directionIconFor(primary.trendDirection),
                                            modifier = Modifier.weight(1f),
                                        )

                                        val pFormatted = TrendAnalyzer.formatPValue(primary.pValue)
                                        MetricCard(
                                            label = "Significance (${primary.methodName})",
                                            value = "p = $pFormatted",
                                            unit = "Threshold α = ${primary.significanceThreshold}",
                                            statusLabel = if (primary.isStatisticallySignificant) "Significant" else "Not established",
                                            statusTone = if (primary.isStatisticallySignificant) StatusTone.SIGNIFICANT else StatusTone.NOT_SIGNIFICANT,
                                            tooltipExplanation = "${primary.interpretationText} ${primary.autocorrelationHandling}",
                                            modifier = Modifier.weight(1f),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        val coveragePct = (1.0 - primary.missingFraction) * 100.0
                                        MetricCard(
                                            label = "Valid Observation Coverage",
                                            value = String.format(Locale.US, "%.1f%%", coveragePct),
                                            unit = "${primary.nValidObservations} of ${primary.nTotalSlots} slots valid",
                                            statusLabel = if (primary.nMissingOrFlagged == 0) "Complete series" else "${primary.nMissingOrFlagged} gaps preserved",
                                            statusTone = if (primary.missingFraction > 0.15) StatusTone.CAUTION else StatusTone.SIGNIFICANT,
                                            tooltipExplanation = "Missing or cloud-flagged NASA observations are never silently converted to zero. Gaps remain blank in charts and excluded from rank pairs.",
                                            modifier = Modifier.weight(1f),
                                        )

                                        val provenanceShort = when (primary.dataProvenanceStatus) {
                                            DataProvenanceStatus.LIVE_NASA_POWER -> "Live API"
                                            DataProvenanceStatus.CACHED_NASA_REFERENCE -> "Verified Cache"
                                            DataProvenanceStatus.OFFLINE_CACHE_FALLBACK -> "Offline Cache"
                                        }
                                        MetricCard(
                                            label = "Data Provenance",
                                            value = provenanceShort,
                                            unit = "Last valid: ${primary.lastObservationDate}",
                                            statusLabel = primary.dataset.shortName,
                                            statusTone = if (primary.dataProvenanceStatus == DataProvenanceStatus.OFFLINE_CACHE_FALLBACK) StatusTone.CAUTION else StatusTone.NEUTRAL,
                                            tooltipExplanation = "Source: ${primary.dataProvenanceStatus.label}. Snapshot/Retrieval: ${primary.retrievedAtIso}.",
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }

                            // Primary Time-Series Chart
                            item {
                                TrendChart(result = primary)
                            }

                            // Regional Summary Map
                            item {
                                RegionMap(
                                    regions = NasaCatalog.regions,
                                    selectedRegion = primary.region,
                                    secondaryRegion = result.comparisonRegionResult?.region,
                                    onSelectRegion = {},
                                    onSelectCustomPoint = null,
                                )
                            }

                            // Multi-Variable Comparison if 2nd variable exists
                            if (result.secondaryResult != null) {
                                item {
                                    ComparisonChart(
                                        primary = primary,
                                        secondary = result.secondaryResult,
                                        correlation = result.comparisonCorrelation,
                                        secondaryTitle = "Two-Variable Comparison (${primary.region.name})",
                                    )
                                }
                            }

                            // Two-Region Comparison if active
                            if (result.comparisonRegionResult != null) {
                                item {
                                    val compCorr = TrendAnalyzer.computeComparisonCorrelation(
                                        primary,
                                        result.comparisonRegionResult,
                                    )
                                    ComparisonChart(
                                        primary = primary,
                                        secondary = result.comparisonRegionResult,
                                        correlation = compCorr,
                                        secondaryTitle = "Regional Comparison: ${primary.region.name} vs ${result.comparisonRegionResult.region.name}",
                                    )
                                }
                            }

                            // What this means / What this does not mean (Spec Section 07.D)
                            item {
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
                                            text = "What this means",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = primary.whatThisMeans,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "What this does not mean",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                        Text(
                                            text = primary.whatThisDoesNotMean,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // Grounded AI Explanation Preview Card (Spec Section 12 + Firebase decline contract)
                            if (aiExplanation != null) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
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
                                                    text = "Plain-Language Synthesis",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AmberContainer,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(
                                                    text = aiExplanation.notice,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(10.dp),
                                                )
                                            }
                                            Text(
                                                text = aiExplanation.summaryParagraph,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = "Caveat: ${aiExplanation.statisticalCaveat}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            // Action Bar (Save, Export, Compare Region, View Evidence, Change Analysis)
                            item {
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
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            PrimaryButton(
                                                text = "Save result",
                                                onClick = onSaveResult,
                                                icon = Icons.Default.BookmarkAdd,
                                                modifier = Modifier.weight(1f),
                                                testTag = "save_result_button",
                                            )
                                            SecondaryButton(
                                                text = "View evidence",
                                                onClick = { showEvidenceFor = primary },
                                                icon = Icons.Default.Verified,
                                                modifier = Modifier.weight(1f),
                                                testTag = "view_evidence_button",
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            SecondaryButton(
                                                text = "Export report",
                                                onClick = { showExportDialog = true },
                                                icon = Icons.Default.Download,
                                                modifier = Modifier.weight(1f),
                                                testTag = "export_report_button",
                                            )
                                            SecondaryButton(
                                                text = "Compare region",
                                                onClick = { showCompareRegionDialog = true },
                                                icon = Icons.Default.CompareArrows,
                                                modifier = Modifier.weight(1f),
                                                testTag = "compare_region_button",
                                            )
                                        }
                                        SecondaryButton(
                                            text = "Change analysis",
                                            onClick = onBackToInvestigate,
                                            icon = Icons.Default.Edit,
                                            modifier = Modifier.fillMaxWidth(),
                                            testTag = "change_analysis_button",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultTopSummaryCard(
    result: InvestigationResult,
    onOpenEvidence: () -> Unit,
) {
    val primary = result.primaryResult
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(
                    label = primary.dataProvenanceStatus.label,
                    tone = when (primary.dataProvenanceStatus) {
                        DataProvenanceStatus.LIVE_NASA_POWER -> StatusTone.SIGNIFICANT
                        DataProvenanceStatus.CACHED_NASA_REFERENCE -> StatusTone.NEUTRAL
                        DataProvenanceStatus.OFFLINE_CACHE_FALLBACK -> StatusTone.CAUTION
                    },
                )
                TextButton(
                    onClick = onOpenEvidence,
                    modifier = Modifier.testTag("top_evidence_link"),
                ) {
                    Text("Evidence & caveats")
                }
            }
            Text(
                text = "${primary.region.name} • ${result.config.startYear}–${result.config.endYear}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Variables: ${listOfNotNull(primary.variable.displayName, result.secondaryResult?.variable?.displayName).joinToString(" + ")} • Last observation: ${primary.lastObservationDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
