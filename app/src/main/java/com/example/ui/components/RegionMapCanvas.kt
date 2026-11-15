package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyRegion
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.TealAccent
import java.util.Locale
import kotlin.math.hypot

/**
 * Interactive Equirectangular World Region Selector & Regional Summary Footprint Map (Spec Sections 07 & 23).
 * Explicitly labels regional aggregates as spatial summaries rather than pixel-level raster tiles.
 */
@Composable
fun RegionMap(
    regions: List<StudyRegion>,
    selectedRegion: StudyRegion?,
    onSelectRegion: (StudyRegion) -> Unit,
    onSelectCustomPoint: ((Double, Double) -> Unit)? = null,
    secondaryRegion: StudyRegion? = null,
    modifier: Modifier = Modifier,
) {
    val oceanColor = MaterialTheme.colorScheme.surfaceVariant
    val landColor = Color(0xFFD8E3E7)
    val landStroke = Color(0xFF9EB2BD)
    val gridColor = Color(0xFFC7D5DC)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(oceanColor)
                    .testTag("region_map_canvas")
                    .pointerInput(regions, onSelectCustomPoint) {
                        detectTapGestures { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            // Check if user tapped near a curated study region marker
                            val hitRegion = regions.minByOrNull { reg ->
                                val rx = ((reg.centroidLon + 180.0) / 360.0).toFloat() * w
                                val ry = ((90.0 - reg.centroidLat) / 180.0).toFloat() * h
                                hypot(offset.x - rx, offset.y - ry)
                            }
                            if (hitRegion != null) {
                                val rx = ((hitRegion.centroidLon + 180.0) / 360.0).toFloat() * w
                                val ry = ((90.0 - hitRegion.centroidLat) / 180.0).toFloat() * h
                                if (hypot(offset.x - rx, offset.y - ry) <= 36f) {
                                    onSelectRegion(hitRegion)
                                    return@detectTapGestures
                                }
                            }
                            if (onSelectCustomPoint != null) {
                                val lon = (offset.x / w) * 360.0 - 180.0
                                val lat = 90.0 - (offset.y / h) * 180.0
                                onSelectCustomPoint(lat.toDouble(), lon.toDouble())
                            }
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    fun toPoint(lat: Double, lon: Double): Offset = Offset(
                        x = ((lon + 180.0) / 360.0 * w).toFloat(),
                        y = ((90.0 - lat) / 180.0 * h).toFloat(),
                    )

                    // Graticule every 30 deg lat / 60 deg lon
                    for (lat in listOf(-60.0, -30.0, 0.0, 30.0, 60.0)) {
                        val y = toPoint(lat, 0.0).y
                        drawLine(
                            color = if (lat == 0.0) landStroke.copy(alpha = 0.6f) else gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = if (lat == 0.0) 1.5f else 1f,
                        )
                    }
                    for (lon in listOf(-120.0, -60.0, 0.0, 60.0, 120.0)) {
                        val x = toPoint(0.0, lon).x
                        drawLine(
                            color = gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f,
                        )
                    }

                    // Simplified schematic continent polygons for clear geographic context
                    val continents = listOf(
                        // North America
                        listOf(70.0 to -165.0, 72.0 to -95.0, 60.0 to -64.0, 45.0 to -55.0, 26.0 to -80.0, 16.0 to -87.0, 10.0 to -78.0, 22.0 to -105.0, 32.0 to -117.0, 58.0 to -138.0),
                        // Greenland / Svalbard region
                        listOf(82.0 to -50.0, 81.0 to -18.0, 68.0 to -25.0, 60.0 to -45.0, 72.0 to -56.0),
                        // South America
                        listOf(11.0 to -74.0, 6.0 to -52.0, -6.0 to -35.0, -23.0 to -41.0, -54.0 to -67.0, -48.0 to -75.0, -18.0 to -71.0, -2.0 to -80.0),
                        // Europe
                        listOf(36.0 to -9.0, 44.0 to -9.0, 52.0 to 4.0, 58.0 to 5.0, 71.0 to 25.0, 66.0 to 42.0, 48.0 to 40.0, 41.0 to 28.0, 38.0 to 15.0),
                        // Africa
                        listOf(35.0 to -6.0, 32.0 to 32.0, 12.0 to 50.0, -10.0 to 40.0, -34.0 to 20.0, -16.0 to 12.0, 5.0 to 8.0, 5.0 to -10.0, 15.0 to -17.0),
                        // Asia
                        listOf(70.0 to 45.0, 75.0 to 105.0, 66.0 to 170.0, 42.0 to 142.0, 22.0 to 114.0, 8.0 to 104.0, 22.0 to 90.0, 8.0 to 77.0, 24.0 to 67.0, 14.0 to 44.0, 38.0 to 36.0),
                        // Australia
                        listOf(-12.0 to 130.0, -12.0 to 143.0, -28.0 to 153.0, -38.0 to 148.0, -35.0 to 116.0, -22.0 to 114.0),
                    )
                    continents.forEach { poly ->
                        val path = Path()
                        poly.forEachIndexed { i, (lat, lon) ->
                            val pt = toPoint(lat, lon)
                            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                        }
                        path.close()
                        drawPath(path, color = landColor)
                        drawPath(path, color = landStroke, style = Stroke(width = 1.5f))
                    }

                    // Draw bounding box + markers for curated study regions
                    regions.forEach { region ->
                        val isSelected = selectedRegion?.id == region.id
                        val isSecondary = secondaryRegion?.id == region.id
                        val tl = toPoint(region.bboxMaxLat, region.bboxMinLon)
                        val br = toPoint(region.bboxMinLat, region.bboxMaxLon)
                        val center = toPoint(region.centroidLat, region.centroidLon)

                        if (isSelected || isSecondary) {
                            val boxColor = if (isSelected) TealAccent else AmberCaution
                            drawRect(
                                color = boxColor.copy(alpha = 0.28f),
                                topLeft = tl,
                                size = Size(
                                    width = (br.x - tl.x).coerceAtLeast(14f),
                                    height = (br.y - tl.y).coerceAtLeast(14f),
                                ),
                            )
                            drawRect(
                                color = boxColor,
                                topLeft = tl,
                                size = Size(
                                    width = (br.x - tl.x).coerceAtLeast(14f),
                                    height = (br.y - tl.y).coerceAtLeast(14f),
                                ),
                                style = Stroke(width = 2.5f),
                            )
                        }

                        drawCircle(
                            color = when {
                                isSelected -> TealAccent
                                isSecondary -> AmberCaution
                                else -> NavyPrimary.copy(alpha = 0.72f)
                            },
                            radius = if (isSelected || isSecondary) 9f else 6f,
                            center = center,
                        )
                        drawCircle(
                            color = Color.White,
                            radius = if (isSelected || isSecondary) 4f else 2.5f,
                            center = center,
                        )
                    }

                    // Custom point pin if active
                    if (selectedRegion?.isCustomPoint == true) {
                        val pt = toPoint(selectedRegion.centroidLat, selectedRegion.centroidLon)
                        drawCircle(color = TealAccent.copy(alpha = 0.25f), radius = 18f, center = pt)
                        drawCircle(color = TealAccent, radius = 8f, center = pt)
                        drawCircle(color = Color.White, radius = 3.5f, center = pt)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = if (onSelectCustomPoint != null) {
                            "Regional Summary Footprint • Tap marker or anywhere for custom point"
                        } else {
                            "Regional Summary Footprint (Not pixel-level raster map)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (selectedRegion != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedRegion.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${selectedRegion.countryOrArea} • ${selectedRegion.regionType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.2f°, %.2f°",
                            selectedRegion.centroidLat,
                            selectedRegion.centroidLon,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = JetBrainsMonoFontFamily,
                    )
                }
            }
        }
    }
}
