package com.janreins.tonnage.ui.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.domain.ExerciseSession
import com.janreins.tonnage.domain.ProgressMetric
import com.janreins.tonnage.ui.components.ChartPoint
import com.janreins.tonnage.ui.components.LineChart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ChartRange(val label: String, val days: Long?) {
    MONTHS_3("3 months", 91),
    YEAR("1 year", 365),
    ALL("All", null),
}

internal fun date(ms: Long, pattern: String): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

/** An exercise's progress chart, with a choice of what to plot and over how long. */
@Composable
fun ExerciseProgressSection(sessions: List<ExerciseSession>, trackingType: TrackingType, modifier: Modifier = Modifier) {
    val metrics = ProgressMetric.forType(trackingType)
    var metricName by rememberSaveable(trackingType) { mutableStateOf(metrics.first().name) }
    var rangeName by rememberSaveable { mutableStateOf(ChartRange.ALL.name) }
    val metric = metrics.firstOrNull { it.name == metricName } ?: metrics.first()
    val range = ChartRange.valueOf(rangeName)

    val cutoff = range.days?.let { System.currentTimeMillis() - it * 24 * 60 * 60 * 1000 }
    val shown = sessions.filter { cutoff == null || it.startedAt >= cutoff }
    var selectedIndex by rememberSaveable(shown.size, metricName, rangeName) { mutableStateOf(shown.lastIndex) }
    val selected = shown.getOrNull(selectedIndex)

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Progress", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { option ->
                    FilterChip(selected = option == metric, onClick = { metricName = option.name }, label = { Text(option.label) })
                }
            }
            if (selected == null) {
                Text("No workouts with this exercise in the last ${range.label}.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column {
                    Text(metric.format(selected.values[metric] ?: 0.0), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${date(selected.startedAt, "EEE d MMM yyyy")} · best set ${selected.bestSet}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LineChart(
                    points = shown.map { ChartPoint(it.startedAt, it.values[metric] ?: 0.0) },
                    selected = selectedIndex,
                    onSelect = { selectedIndex = it },
                    formatValue = metric::format,
                    description = "${metric.label} over ${shown.size} workouts, from " +
                        "${metric.format(shown.first().values[metric] ?: 0.0)} to ${metric.format(shown.last().values[metric] ?: 0.0)}",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(date(shown.first().startedAt, "d MMM yy"), style = MaterialTheme.typography.labelSmall)
                    if (shown.size > 1) Text(date(shown.last().startedAt, "d MMM yy"), style = MaterialTheme.typography.labelSmall)
                }
                val best = shown.maxBy { it.values[metric] ?: 0.0 }
                Text(
                    "Best: ${metric.format(best.values[metric] ?: 0.0)} on ${date(best.startedAt, "d MMM yyyy")} · " +
                        "${shown.size} ${if (shown.size == 1) "workout" else "workouts"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { option ->
                    FilterChip(selected = option == range, onClick = { rangeName = option.name }, label = { Text(option.label) })
                }
            }
        }
    }
}
