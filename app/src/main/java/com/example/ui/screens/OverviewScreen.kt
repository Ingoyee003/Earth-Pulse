package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.catalog.NasaCatalog
import com.example.data.model.FeaturedInvestigation
import com.example.ui.components.AppFooter
import com.example.ui.components.AppHeader
import com.example.ui.components.DatasetBadge
import com.example.ui.components.PrimaryButton
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.TealAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(
    onStartInvestigation: () -> Unit,
    onLaunchDemo: (FeaturedInvestigation) -> Unit,
    onOpenAboutData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppHeader(
            title = "EARTH PULSE",
            subtitle = "Autonomous Earth System Trend Discovery",
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Hero Card
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = NavyPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(NavyDeep, NavyPrimary, TealAccent),
                                    ),
                                )
                                .padding(20.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatusBadge(
                                    label = "NASA Earth-Observation Provenance",
                                    tone = StatusTone.NEUTRAL,
                                    icon = Icons.Default.Science,
                                )
                                Text(
                                    text = "The Earth is changing.\nWe detect what others overlook.",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Select a region and NASA Earth-science variables, inspect satellite and reanalysis time series over time, and test statistical trend significance with full provenance.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.88f),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                PrimaryButton(
                                    text = "Start investigation",
                                    onClick = onStartInvestigation,
                                    icon = Icons.Default.Explore,
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "overview_start_investigation_button",
                                )
                            }
                        }
                    }
                }

                // How it works strip (Select -> Analyse -> Understand)
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "How it works",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                WorkflowStepChip(
                                    stepNum = "1",
                                    title = "Select",
                                    detail = "Place, variables & window",
                                    icon = Icons.Default.LocationOn,
                                    modifier = Modifier.weight(1f),
                                )
                                WorkflowStepChip(
                                    stepNum = "2",
                                    title = "Analyse",
                                    detail = "Sen slope & Mann–Kendall",
                                    icon = Icons.Default.Analytics,
                                    modifier = Modifier.weight(1f),
                                )
                                WorkflowStepChip(
                                    stepNum = "3",
                                    title = "Understand",
                                    detail = "Evidence & limitations",
                                    icon = Icons.Default.Insights,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // Featured Demo Investigations
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Featured Investigations",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Launch a prepared NASA case study in one tap to inspect real trends, seasonal cycles, and documented missing-data gaps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(NasaCatalog.featuredInvestigations.size) { index ->
                    val demo = NasaCatalog.featuredInvestigations[index]
                    val region = NasaCatalog.regionFor(demo.regionId)
                    val vars = demo.variableIds.map { NasaCatalog.variableFor(it) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLaunchDemo(demo) }
                            .testTag("featured_demo_${demo.id}"),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StatusBadge(
                                    label = demo.badgeLabel,
                                    tone = StatusTone.CAUTION,
                                )
                                Text(
                                    text = "${demo.startYear}–${demo.endYear}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = demo.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = demo.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                DatasetBadge(text = region.name)
                                vars.forEach { v ->
                                    DatasetBadge(text = "${v.shortLabel} (${v.unit})")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Run guided investigation",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // Data Coverage Strip (only integrated NASA datasets/variables)
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
                                text = "Integrated NASA Data Coverage",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            NasaCatalog.datasets.forEach { ds ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${ds.shortName} • ${ds.temporalResolution}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${ds.spatialResolution} • ${ds.accessMethod}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    AppFooter(onOpenAboutData = onOpenAboutData)
                }
            }
        }
    }
}

@Composable
private fun WorkflowStepChip(
    stepNum: String,
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "$stepNum. $title",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
