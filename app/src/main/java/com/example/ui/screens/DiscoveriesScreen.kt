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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.catalog.NasaCatalog
import com.example.data.model.FeaturedInvestigation
import com.example.data.model.SavedDiscoverySummary
import com.example.data.model.TrendDirection
import com.example.ui.components.AppHeader
import com.example.ui.components.DatasetBadge
import com.example.ui.components.EmptyState
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.directionIconFor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.DiscoveriesFilterState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveriesScreen(
    savedDiscoveries: List<SavedDiscoverySummary>,
    filterState: DiscoveriesFilterState,
    onFilterChange: (
        regionQuery: String,
        variableFilterId: String?,
        directionFilter: TrendDirection?,
        significantOnly: Boolean,
    ) -> Unit,
    onOpenSavedDiscovery: (SavedDiscoverySummary) -> Unit,
    onDeleteDiscovery: (String) -> Unit,
    onLaunchDemo: (FeaturedInvestigation) -> Unit,
    onStartNewInvestigation: () -> Unit,
    onOpenAboutData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppHeader(
            title = "DISCOVERIES LIBRARY",
            subtitle = "Saved investigations & curated NASA findings",
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Local Persistence Notice (Spec Section 07.E)
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Saved discoveries are stored locally on this device and do not sync across devices. Use Export report to share CSV or JSON files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Filters
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = filterState.regionQuery,
                            onValueChange = {
                                onFilterChange(
                                    it,
                                    filterState.variableFilterId,
                                    filterState.directionFilter,
                                    filterState.significantOnly,
                                )
                            },
                            placeholder = { Text("Filter saved findings by region or title") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                selected = filterState.significantOnly,
                                onClick = {
                                    onFilterChange(
                                        filterState.regionQuery,
                                        filterState.variableFilterId,
                                        filterState.directionFilter,
                                        !filterState.significantOnly,
                                    )
                                },
                                label = { Text("Significant only") },
                            )
                            TrendDirection.entries.forEach { dir ->
                                FilterChip(
                                    selected = filterState.directionFilter == dir,
                                    onClick = {
                                        val nextDir = if (filterState.directionFilter == dir) null else dir
                                        onFilterChange(
                                            filterState.regionQuery,
                                            filterState.variableFilterId,
                                            nextDir,
                                            filterState.significantOnly,
                                        )
                                    },
                                    label = { Text(dir.label) },
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            NasaCatalog.variables.forEach { v ->
                                FilterChip(
                                    selected = filterState.variableFilterId == v.id,
                                    onClick = {
                                        val nextVar = if (filterState.variableFilterId == v.id) null else v.id
                                        onFilterChange(
                                            filterState.regionQuery,
                                            nextVar,
                                            filterState.directionFilter,
                                            filterState.significantOnly,
                                        )
                                    },
                                    label = { Text(v.shortLabel) },
                                )
                            }
                        }
                    }
                }

                // Saved Investigations List
                item {
                    Text(
                        text = "Your Saved Investigations (${savedDiscoveries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                if (savedDiscoveries.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No saved investigations yet",
                            message = "Run an investigation and tap 'Save result' in the Results Workspace to store its settings, rate, significance, and provenance here.",
                            actionLabel = "Start investigation",
                            onAction = onStartNewInvestigation,
                        )
                    }
                } else {
                    items(savedDiscoveries, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenSavedDiscovery(item) }
                                .testTag("saved_discovery_${item.id}"),
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
                                        label = if (item.isSignificant) "Significant" else "Not significant",
                                        tone = if (item.isSignificant) StatusTone.SIGNIFICANT else StatusTone.NOT_SIGNIFICANT,
                                        icon = directionIconFor(item.direction),
                                    )
                                    IconButton(
                                        onClick = { onDeleteDiscovery(item.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete saved discovery",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${item.slopeFormatted} • p = ${item.pValueFormatted} • ${item.periodLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DatasetBadge(text = item.regionName)
                                    DatasetBadge(text = item.primaryVariableName)
                                    DatasetBadge(text = "Saved ${item.savedAtIso.take(10)}")
                                }
                            }
                        }
                    }
                }

                // Curated Reference Examples
                item {
                    Text(
                        text = "Curated NASA Reference Cases",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                items(NasaCatalog.featuredInvestigations, key = { it.id }) { demo ->
                    val reg = NasaCatalog.regionFor(demo.regionId)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLaunchDemo(demo) },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = demo.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${reg.name} • ${demo.startYear}–${demo.endYear}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusBadge(label = "DEMO", tone = StatusTone.CAUTION)
                        }
                    }
                }
            }
        }
    }
}
