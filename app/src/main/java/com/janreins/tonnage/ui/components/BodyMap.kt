package com.janreins.tonnage.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Space between the front and back figures, in figure units. */
private const val GAP = 16f

/** Where the back figure starts, in figure units. */
const val BODY_MAP_BACK_X = BodyMapGeometry.WIDTH + GAP
const val BODY_MAP_TOTAL_WIDTH = BodyMapGeometry.WIDTH * 2 + GAP

/** The colour of a muscle at [shade]: unworked muscles are grey, worked ones deepen to the accent colour. */
@Composable
fun bodyMapColor(shade: Float): Color {
    val idle = MaterialTheme.colorScheme.outline
    return if (shade <= 0f) idle else lerp(idle, MaterialTheme.colorScheme.primary, shade)
}

private class RegionPath(val muscle: String?, val path: Path)

private fun pathsFor(regions: List<BodyRegion>): List<RegionPath> = regions.map { region ->
    val path = Path().apply {
        moveTo(region.points.first().x, region.points.first().y)
        BodyMapGeometry.curves(region.points).forEach { c ->
            cubicTo(c.control1.x, c.control1.y, c.control2.x, c.control2.y, c.end.x, c.end.y)
        }
        close()
    }
    RegionPath(region.muscle, path)
}

/**
 * Front and back body figures with each muscle shaded by its number of sets in [setsByMuscle].
 * Tapping a muscle calls [onSelect] with its name (or null for empty space); [selected] is outlined.
 */
@Composable
fun BodyMap(
    setsByMuscle: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val body = MaterialTheme.colorScheme.surface
    // The filled card's colour, so neighbouring muscles are separated by a thin gap.
    val divider = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.onSurface
    val most = setsByMuscle.filterKeys { it in BodyMapGeometry.muscles }.values.maxOrNull() ?: 0
    val colors = BodyMapGeometry.muscles.associateWith { bodyMapColor(BodyMapGeometry.shade(setsByMuscle[it] ?: 0, most)) }
    val silhouette = remember { pathsFor(BodyMapGeometry.silhouette) }
    val front = remember { pathsFor(BodyMapGeometry.front) }
    val back = remember { pathsFor(BodyMapGeometry.back) }
    val select by rememberUpdatedState(onSelect)

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(BODY_MAP_TOTAL_WIDTH / BodyMapGeometry.HEIGHT)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val factor = size.height / BodyMapGeometry.HEIGHT
                    val left = (size.width - BODY_MAP_TOTAL_WIDTH * factor) / 2
                    val x = (tap.x - left) / factor
                    val y = tap.y / factor
                    select(
                        when {
                            x in 0f..BodyMapGeometry.WIDTH -> BodyMapGeometry.muscleAt(BodyMapGeometry.front, x, y)
                            x >= BODY_MAP_BACK_X -> BodyMapGeometry.muscleAt(BodyMapGeometry.back, x - BODY_MAP_BACK_X, y)
                            else -> null
                        },
                    )
                }
            },
    ) {
        val factor = size.height / BodyMapGeometry.HEIGHT
        val left = (size.width - BODY_MAP_TOTAL_WIDTH * factor) / 2
        listOf(0f to front, BODY_MAP_BACK_X to back).forEach { (x, muscles) ->
            withTransform({
                translate(left + x * factor, 0f)
                scale(factor, factor, pivot = Offset.Zero)
            }) {
                drawFigure(silhouette, muscles, body, divider, highlight, colors, selected)
            }
        }
    }
}

private fun DrawScope.drawFigure(
    silhouette: List<RegionPath>,
    muscles: List<RegionPath>,
    body: Color,
    divider: Color,
    highlight: Color,
    colors: Map<String, Color>,
    selected: String?,
) {
    val head = BodyMapGeometry.headCenter
    drawOval(
        body,
        topLeft = Offset(head.x - BodyMapGeometry.HEAD_RADIUS_X, head.y - BodyMapGeometry.HEAD_RADIUS_Y),
        size = Size(BodyMapGeometry.HEAD_RADIUS_X * 2, BodyMapGeometry.HEAD_RADIUS_Y * 2),
    )
    silhouette.forEach { drawPath(it.path, body) }
    muscles.forEach { region ->
        drawPath(region.path, colors[region.muscle] ?: body)
        drawPath(region.path, divider, style = Stroke(width = 0.8f, join = StrokeJoin.Round))
    }
    muscles.filter { it.muscle != null && it.muscle == selected }.forEach {
        drawPath(it.path, highlight, style = Stroke(width = 1.6f, join = StrokeJoin.Round))
    }
}
