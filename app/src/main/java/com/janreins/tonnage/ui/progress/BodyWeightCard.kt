package com.janreins.tonnage.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.janreins.tonnage.data.model.BodyWeight
import com.janreins.tonnage.domain.BodyWeights
import com.janreins.tonnage.domain.WeightRange
import com.janreins.tonnage.ui.components.ChartPoint
import com.janreins.tonnage.ui.components.LineChart
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Body weight over time, with the latest weight and the change over the chosen range. */
@Composable
fun BodyWeightCard(entries: List<BodyWeight>, today: LocalDate, modifier: Modifier = Modifier) {
    var rangeName by rememberSaveable { mutableStateOf(WeightRange.MONTHS_3.name) }
    val range = WeightRange.valueOf(rangeName)
    val trend = BodyWeights.trend(entries, range, today)
    var selected by rememberSaveable(trend?.shown?.size, rangeName) { mutableStateOf(trend?.shown?.lastIndex ?: 0) }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Body weight", style = MaterialTheme.typography.titleMedium)
            if (trend == null) {
                Text("No entries in the last ${range.label}.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val since = if (range == WeightRange.ALL) "since ${trend.shown.first().date.format(shortDate)}" else "in ${range.label}"
                Text(
                    BodyWeights.kg(trend.latest.weightKg) + (trend.changeKg?.let { ", ${BodyWeights.change(it)} $since" } ?: ""),
                    style = MaterialTheme.typography.headlineSmall,
                )
                val point = trend.shown.getOrNull(selected)
                if (point != null && point != trend.latest) {
                    Text(
                        "${point.date.format(shortDate)}: ${BodyWeights.kg(point.weightKg)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LineChart(
                    points = trend.shown.map { ChartPoint(it.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), it.weightKg) },
                    selected = selected,
                    onSelect = { selected = it },
                    formatValue = BodyWeights::kg,
                    description = "Body weight over ${trend.shown.size} entries, from " +
                        "${BodyWeights.kg(trend.shown.first().weightKg)} to ${BodyWeights.kg(trend.latest.weightKg)}",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightRange.entries.forEach { option ->
                    FilterChip(selected = option == range, onClick = { rangeName = option.name }, label = { Text(option.label) })
                }
            }
        }
    }
}
