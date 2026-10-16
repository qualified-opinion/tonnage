package com.janreins.tonnage.ui.components

import android.graphics.Matrix
import android.graphics.RadialGradient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

// The artwork keeps its own light-grey palette in both themes, like an anatomy chart.
private val Skin = Color(0xFFE0E0E0)
private val Neutral = Color(0xFFC9C9C9)
private val Idle = Color(0xFFBDBDBD)
private val LightlyWorked = Color(0xFFA8DDB9)
private val MostWorked = Color(0xFF1B7A45)
private val DetailLine = Color.Black.copy(alpha = 0.38f)

/** Space between the front and back figures, in artwork units. */
private const val GAP = 40f

/** The colour of a muscle at [shade]: unworked muscles are grey, worked ones deepen from light to dark green. */
fun bodyMapColor(shade: Float): Color = if (shade <= 0f) Idle else lerp(LightlyWorked, MostWorked, shade.coerceIn(0f, 1f))

private object ArtCache {
    @Volatile var art: BodyMapArt? = null
}

/** The body artwork, read from the app's assets once and kept for the rest of the session. */
@Composable
fun rememberBodyMapArt(): BodyMapArt {
    val context = LocalContext.current
    return remember {
        ArtCache.art ?: BodyMapArt.parse(
            context.assets.open(BodyMapArt.ASSET_NAME).bufferedReader().use { it.readText() },
        ).also { ArtCache.art = it }
    }
}

private class DrawnShape(val shape: BodyShape, val path: Path)

private fun drawnShapes(view: BodyView): List<DrawnShape> = view.shapes.map { shape ->
    val path = Path().apply {
        fillType = if (shape.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
        shape.steps.forEach { step ->
            when (step) {
                is PathStep.MoveTo -> moveTo(step.x, step.y)
                is PathStep.LineTo -> lineTo(step.x, step.y)
                is PathStep.CubicTo -> cubicTo(step.x1, step.y1, step.x2, step.y2, step.x, step.y)
                PathStep.Close -> close()
            }
        }
    }
    DrawnShape(shape, path)
}

/**
 * A soft highlight towards the top left of a shape and shadow towards its edge, stretched to the
 * shape's box so each muscle looks rounded.
 */
private fun shading(shape: BodyShape, base: Color): Brush {
    val gradient = RadialGradient(
        0.4f,
        0.35f,
        0.75f,
        intArrayOf(lerp(base, Color.White, 0.22f).toArgb(), base.toArgb(), lerp(base, Color.Black, 0.22f).toArgb()),
        floatArrayOf(0f, 0.6f, 1f),
        android.graphics.Shader.TileMode.CLAMP,
    )
    gradient.setLocalMatrix(
        Matrix().apply {
            setScale(shape.right - shape.left, shape.bottom - shape.top)
            postTranslate(shape.left, shape.top)
        },
    )
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = gradient
    }
}

/**
 * Front and back anatomy figures with each muscle shaded by its number of sets in [setsByMuscle].
 * Tapping calls [onSelect] with the muscle under the finger (null for skin or empty space); every
 * shape of the [selected] muscle group is outlined.
 */
@Composable
fun BodyMap(
    setsByMuscle: Map<String, Int>,
    selected: String?,
    onSelect: (BodyShape?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val art = rememberBodyMapArt()
    val highlight = MaterialTheme.colorScheme.onSurface
    val most = setsByMuscle.filterKeys { it in art.muscles }.values.maxOrNull() ?: 0
    val colors = art.muscles.associateWith { bodyMapColor(BodyMapArt.shade(setsByMuscle[it] ?: 0, most)) }
    val front = remember(art) { drawnShapes(art.front) }
    val back = remember(art) { drawnShapes(art.back) }
    val brushes = remember(art, colors) {
        (front + back).associateWith { drawn ->
            when (drawn.shape.kind) {
                ShapeKind.NEUTRAL -> shading(drawn.shape, Neutral)
                ShapeKind.MUSCLE -> shading(drawn.shape, colors[drawn.shape.muscle] ?: Idle)
                else -> null
            }
        }
    }
    val select by rememberUpdatedState(onSelect)
    // Both figures are drawn at the front figure's height.
    val backScale = art.front.height / art.back.height
    val totalWidth = art.front.width + GAP + art.back.width * backScale
    val backX = art.front.width + GAP

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(totalWidth / art.front.height)
                .semantics { contentDescription = description }
                .pointerInput(art) {
                    detectTapGestures { tap ->
                        val factor = size.height / art.front.height
                        val left = (size.width - totalWidth * factor) / 2
                        val x = (tap.x - left) / factor
                        val y = tap.y / factor
                        select(
                            when {
                                x in 0f..art.front.width -> art.front.regionAt(x, y)
                                x >= backX -> art.back.regionAt((x - backX) / backScale, y / backScale)
                                else -> null
                            },
                        )
                    }
                },
        ) {
            val factor = size.height / art.front.height
            val left = (size.width - totalWidth * factor) / 2
            listOf(Triple(0f, 1f, front), Triple(backX, backScale, back)).forEach { (x, scale, shapes) ->
                withTransform({
                    translate(left + x * factor, 0f)
                    scale(factor * scale, factor * scale, pivot = Offset.Zero)
                }) {
                    drawFigure(shapes, brushes, highlight, selected)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Front" to art.front.width, "" to GAP, "Back" to art.back.width * backScale).forEach { (label, width) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(width),
                )
            }
        }
    }
}

private fun DrawScope.drawFigure(
    shapes: List<DrawnShape>,
    brushes: Map<DrawnShape, Brush?>,
    highlight: Color,
    selected: String?,
) {
    shapes.forEach { drawn ->
        when (drawn.shape.kind) {
            ShapeKind.BODY -> drawPath(drawn.path, Skin)
            ShapeKind.LINE -> drawPath(
                drawn.path,
                DetailLine,
                style = Stroke(width = drawn.shape.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            else -> drawPath(drawn.path, brushes[drawn] ?: SolidColor(Idle))
        }
    }
    shapes.filter { it.shape.kind == ShapeKind.MUSCLE && it.shape.muscle == selected }.forEach {
        drawPath(it.path, highlight, style = Stroke(width = 4f, join = StrokeJoin.Round))
    }
}
