package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ComparisonCorrelation
import com.example.data.model.QualityStatus
import com.example.data.model.TimeSeriesPoint
import com.example.data.model.VariableTrendResult
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.CoralNegative
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.TealAccent
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Interactive Time-Series & Trend Chart (Spec Sections 08 & 10).
 * - Displays axis labels, units, start/mid/end dates, and legend.
 * - Distinguishes observed data from fitted trend line.
 * - Explicitly breaks lines across missing/flagged gaps and shades missing intervals.
 * - Always provides a concise accessible text summary below the chart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendChart(
    result: VariableTrendResult,
    accentColor: Color = TealAccent,
    modifier: Modifier = Modifier,
) {
    val validValues = remember(result.series) {
        result.series.mapNotNull { if (it.qualityStatus == QualityStatus.VALID) it.value else null }
    }
    var selectedPoint by remember(result) { mutableStateOf<TimeSeriesPoint?>(null) }

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val trendColor = CoralNegative

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("trend_chart_${result.variable.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${result.variable.displayName} (${result.variable.unit})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${result.region.name} • ${result.aggregationName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectedPoint != null) {
                    val pt = selectedPoint!!
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        val valLabel = if (pt.qualityStatus == QualityStatus.VALID && pt.value != null) {
                            String.format(Locale.US, "%.2f %s", pt.value, result.variable.unit)
                        } else {
                            "Missing (${pt.sourceFlag})"
                        }
                        Text(
                            text = "${pt.yearMonth}: $valLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (validValues.isEmpty() || result.series.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No valid observations available to plot.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val allY = buildList {
                    addAll(validValues)
                    result.trendLine?.let {
                        add(it.startValue)
                        add(it.endValue)
                    }
                }
                val rawMin = allY.minOrNull() ?: 0.0
                val rawMax = allY.maxOrNull() ?: 1.0
                val pad = ((rawMax - rawMin) * 0.12).coerceAtLeast(0.1)
                val yMin = rawMin - pad
                val yMax = rawMax + pad

                val tMin = result.series.first().decimalYear
                val tMax = result.series.last().decimalYear.coerceAtLeast(tMin + 0.1)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Y-axis labels
                    Column(
                        modifier = Modifier
                            .height(190.dp)
                            .padding(end = 6.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", yMax),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", 0.5 * (yMax + yMin)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", yMin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(190.dp)
                            .pointerInput(result.series) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val frac = (offset.x / w).coerceIn(0f, 1f)
                                    val targetYear = tMin + frac * (tMax - tMin)
                                    val closest = result.series.minByOrNull { abs(it.decimalYear - targetYear) }
                                    selectedPoint = closest
                                }
                            },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val ySpan = (yMax - yMin).coerceAtLeast(1e-6)
                            val tSpan = (tMax - tMin).coerceAtLeast(1e-6)

                            fun xOf(t: Double): Float = (((t - tMin) / tSpan) * w).toFloat()
                            fun yOf(v: Double): Float = (h - ((v - yMin) / ySpan) * h).toFloat()

                            // Horizontal gridlines
                            for (step in 0..4) {
                                val y = h * (step / 4f)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1f,
                                )
                            }

                            // Highlight missing/cloud-flagged gaps explicitly (never bridged!)
                            val slotWidth = (w / result.series.size.coerceAtLeast(1)).coerceAtLeast(3f)
                            result.series.forEach { pt ->
                                if (pt.qualityStatus != QualityStatus.VALID || pt.value == null) {
                                    val gx = xOf(pt.decimalYear)
                                    drawRect(
                                        color = AmberCaution.copy(alpha = 0.20f),
                                        topLeft = Offset((gx - slotWidth / 2f).coerceAtLeast(0f), 0f),
                                        size = Size(slotWidth, h),
                                    )
                                }
                            }

                            // Break observed polyline strictly across missing slots
                            var currentPath: Path? = null
                            result.series.forEach { pt ->
                                if (pt.qualityStatus == QualityStatus.VALID && pt.value != null) {
                                    val px = xOf(pt.decimalYear)
                                    val py = yOf(pt.value)
                                    if (currentPath == null) {
                                        currentPath = Path().apply { moveTo(px, py) }
                                    } else {
                                        currentPath?.lineTo(px, py)
                                    }
                                    drawCircle(
                                        color = accentColor,
                                        radius = 2.8f,
                                        center = Offset(px, py),
                                    )
                                } else {
                                    currentPath?.let {
                                        drawPath(it, color = accentColor, style = Stroke(width = 3f))
                                    }
                                    currentPath = null
                                }
                            }
                            currentPath?.let {
                                drawPath(it, color = accentColor, style = Stroke(width = 3f))
                            }

                            // Fitted trend line
                            result.trendLine?.let { tl ->
                                drawLine(
                                    color = trendColor,
                                    start = Offset(xOf(tl.startYear), yOf(tl.startValue)),
                                    end = Offset(xOf(tl.endYear), yOf(tl.endValue)),
                                    strokeWidth = 4.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f),
                                )
                            }

                            // Selected point marker
                            selectedPoint?.let { sp ->
                                val sx = xOf(sp.decimalYear)
                                drawLine(
                                    color = NavyPrimary.copy(alpha = 0.55f),
                                    start = Offset(sx, 0f),
                                    end = Offset(sx, h),
                                    strokeWidth = 2f,
                                )
                                if (sp.value != null) {
                                    drawCircle(
                                        color = NavyPrimary,
                                        radius = 6f,
                                        center = Offset(sx, yOf(sp.value)),
                                    )
                                }
                            }
                        }
                    }
                }

                // X-axis date ticks
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 38.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val midIdx = result.series.size / 2
                    Text(
                        text = result.series.first().yearMonth,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (midIdx in result.series.indices) {
                        Text(
                            text = result.series[midIdx].yearMonth,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = result.series.last().yearMonth,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Legend
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LegendItem(color = accentColor, label = "Observed (${result.variable.unit})")
                    if (result.trendLine != null) {
                        LegendItem(color = trendColor, label = "Fitted trend (${result.slopeEstimatorName})")
                    }
                    if (result.nMissingOrFlagged > 0) {
                        LegendItem(
                            color = AmberCaution,
                            label = "Missing/flagged gap (${result.nMissingOrFlagged})",
                        )
                    }
                }

                // Accessible Text Summary (Spec Section 08 & 18)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = result.chartTextSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComparisonChart(
    primary: VariableTrendResult,
    secondary: VariableTrendResult,
    correlation: ComparisonCorrelation?,
    secondaryTitle: String = "Multi-Variable Comparison",
    modifier: Modifier = Modifier,
) {
    var normalizedZView by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = secondaryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${primary.variable.shortLabel} (${primary.variable.unit}) vs ${secondary.variable.shortLabel} (${secondary.variable.unit})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Never overlay different units on an unlabeled axis: offer separate panels or explicit Z-score normalized view
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !normalizedZView,
                    onClick = { normalizedZView = false },
                    label = { Text("Separate Labeled Axes") },
                )
                FilterChip(
                    selected = normalizedZView,
                    onClick = { normalizedZView = true },
                    label = { Text("Normalized Z-Score Overlay") },
                )
            }

            if (!normalizedZView) {
                TrendChart(result = secondary, accentColor = NavyPrimary)
            } else {
                NormalizedZScoreOverlayCanvas(primary = primary, secondary = secondary)
            }

            if (correlation != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val rStr = correlation.pearsonR?.let { String.format(Locale.US, "%.3f", it) } ?: "N/A"
                        Text(
                            text = "Concurrent Pearson r = $rStr (n = ${correlation.overlapCount} overlapping months)",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = correlation.caveat,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NormalizedZScoreOverlayCanvas(
    primary: VariableTrendResult,
    secondary: VariableTrendResult,
) {
    val zPrimary = remember(primary.series) { toZScores(primary.series) }
    val zSecondary = remember(secondary.series) { toZScores(secondary.series) }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Normalized Standard Deviations (Z-score, unitless) — strictly for pattern comparison",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val allZ = (zPrimary.mapNotNull { it.second } + zSecondary.mapNotNull { it.second })
                val maxAbs = (allZ.maxOfOrNull { abs(it) } ?: 2.0).coerceAtLeast(2.0)
                val tMin = primary.series.firstOrNull()?.decimalYear ?: 2000.0
                val tMax = (primary.series.lastOrNull()?.decimalYear ?: 2024.0).coerceAtLeast(tMin + 0.1)

                fun xOf(t: Double): Float = (((t - tMin) / (tMax - tMin)) * w).toFloat()
                fun yOf(z: Double): Float = (h * 0.5f - (z / (maxAbs * 1.15)) * (h * 0.5f)).toFloat()

                // Zero baseline & grid
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 1.5f,
                )

                fun drawBrokenSeries(pts: List<Pair<Double, Double?>>, color: Color) {
                    var path: Path? = null
                    pts.forEach { (t, z) ->
                        if (z != null) {
                            val px = xOf(t)
                            val py = yOf(z)
                            if (path == null) {
                                path = Path().apply { moveTo(px, py) }
                            } else {
                                path?.lineTo(px, py)
                            }
                        } else {
                            path?.let { drawPath(it, color = color, style = Stroke(width = 3f)) }
                            path = null
                        }
                    }
                    path?.let { drawPath(it, color = color, style = Stroke(width = 3f)) }
                }

                drawBrokenSeries(zPrimary, TealAccent)
                drawBrokenSeries(zSecondary, AmberCaution)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(color = TealAccent, label = "${primary.variable.shortLabel} (Z)")
            LegendItem(color = AmberCaution, label = "${secondary.variable.shortLabel} (Z)")
        }
    }
}

private fun toZScores(series: List<TimeSeriesPoint>): List<Pair<Double, Double?>> {
    val valid = series.mapNotNull { if (it.qualityStatus == QualityStatus.VALID) it.value else null }
    if (valid.size < 2) return series.map { it.decimalYear to null }
    val mean = valid.average()
    val variance = valid.sumOf { (it - mean) * (it - mean) } / (valid.size - 1)
    val std = sqrt(variance).coerceAtLeast(1e-9)
    return series.map { pt ->
        val z = if (pt.qualityStatus == QualityStatus.VALID && pt.value != null) {
            (pt.value - mean) / std
        } else {
            null
        }
        pt.decimalYear to z
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
