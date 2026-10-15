package com.janreins.tonnage.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A point on a line chart: [x] is a time in ms, [y] the value. */
data class ChartPoint(val x: Long, val y: Double)

/**
 * A line over time with three value gridlines. Tapping picks the nearest point, which is drawn
 * larger; the caller shows its value (charts are drawn by hand to keep the app dependency-free).
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    formatValue: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val select by rememberUpdatedState(onSelect)
    if (points.isEmpty()) return

    val low = points.minOf { it.y }
    val high = points.maxOf { it.y }
    val pad = if (high > low) (high - low) * 0.1 else maxOf(high * 0.1, 1.0)
    val min = (low - pad).coerceAtLeast(0.0)
    val max = high + pad
    val first = points.first().x
    val last = points.last().x

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val width = size.width.toFloat()
                    select(points.indices.minBy { index -> abs(xFor(points[index].x, first, last, width) - tap.x) })
                }
            },
    ) {
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        fun y(value: Double): Float = (bottom - (value - min) / (max - min) * (bottom - top)).toFloat()

        // Gridlines with their values, labels sitting just above each line.
        listOf(min, (min + max) / 2, max).forEach { value ->
            val gy = y(value)
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(formatValue(value), labelStyle)
            val ty = (gy - text.size.height - 2.dp.toPx()).coerceAtLeast(0f)
            drawText(text, topLeft = Offset(0f, ty))
        }

        val offsets = points.map { Offset(xFor(it.x, first, last, size.width), y(it.y)) }
        if (offsets.size > 1) {
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        offsets.forEachIndexed { index, offset ->
            val chosen = index == selected
            drawCircle(line, radius = if (chosen) 6.dp.toPx() else 3.dp.toPx(), center = offset)
            if (chosen) drawLine(grid, Offset(offset.x, top), Offset(offset.x, bottom), strokeWidth = 1.dp.toPx())
        }
    }
}

/** Where time [x] falls across the chart; a single point sits in the middle. */
private fun xFor(x: Long, first: Long, last: Long, width: Float): Float {
    val inset = 8f
    if (last == first) return width / 2
    return inset + (x - first).toFloat() / (last - first) * (width - 2 * inset)
}

/**
 * One bar per week with its value above it; the selected bar is drawn in the primary colour and
 * the rest muted. Tapping a bar selects it. [labels] are drawn under every third bar from the right.
 */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val active = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    val select by rememberUpdatedState(onSelect)
    if (values.isEmpty()) return
    val max = maxOf(values.max(), 1)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(values.size) {
                detectTapGestures { tap ->
                    val slot = size.width.toFloat() / values.size
                    select((tap.x / slot).toInt().coerceIn(0, values.lastIndex))
                }
            },
    ) {
        val slot = size.width / values.size
        val barWidth = slot * 0.6f
        val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
        val valueHeight = measurer.measure("0", valueStyle).size.height.toFloat()
        val bottom = size.height - labelHeight - 4.dp.toPx()
        val top = valueHeight + 2.dp.toPx()
        values.forEachIndexed { index, value ->
            val left = index * slot + (slot - barWidth) / 2
            val barHeight = if (value == 0) 2.dp.toPx() else (bottom - top) * value / max
            drawRoundRect(
                color = if (index == selected) active else muted,
                topLeft = Offset(left, bottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            if (value > 0) {
                val text = measurer.measure(value.toString(), valueStyle)
                drawText(text, topLeft = Offset(left + (barWidth - text.size.width) / 2, bottom - barHeight - text.size.height - 2.dp.toPx()))
            }
            if ((values.lastIndex - index) % 3 == 0) {
                val text = measurer.measure(labels.getOrElse(index) { "" }, labelStyle)
                val x = (left + (barWidth - text.size.width) / 2).coerceIn(0f, size.width - text.size.width)
                drawText(text, topLeft = Offset(x, size.height - text.size.height))
            }
        }
    }
}
