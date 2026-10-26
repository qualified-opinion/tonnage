package com.janreins.tonnage.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.domain.ExerciseSession
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.domain.ProgressMetric

private const val STALLED_AFTER = 3

/** The newest sessions of an exercise as a compact table, with a note when it has stopped improving. */
@Composable
fun RecentSessionsCard(sessions: List<ExerciseSession>, trackingType: TrackingType, modifier: Modifier = Modifier) {
    if (sessions.isEmpty()) return
    val headline = ProgressMetric.forType(trackingType).first()
    val showVolume = trackingType == TrackingType.WEIGHT_REPS
    val stalled = Progress.sessionsSinceBest(sessions, headline)
    val rows = Progress.newestSessions(sessions)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent sessions", style = MaterialTheme.typography.titleMedium)
            if (stalled >= STALLED_AFTER) {
                Text(
                    "No new best in the last $stalled sessions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            TableRow {
                Cell("Date", 1.3f, MaterialTheme.typography.labelSmall, muted, TextAlign.Start)
                Cell("Sets", 0.6f, MaterialTheme.typography.labelSmall, muted)
                Cell("Best set", 1.4f, MaterialTheme.typography.labelSmall, muted)
                Cell(headline.label, 1.1f, MaterialTheme.typography.labelSmall, muted)
                if (showVolume) Cell("Volume", 1.1f, MaterialTheme.typography.labelSmall, muted)
            }
            rows.forEach { session ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                TableRow {
                    val style = MaterialTheme.typography.bodySmall
                    val color = MaterialTheme.colorScheme.onSurface
                    Cell(date(session.startedAt, "d MMM yyyy"), 1.3f, style, color, TextAlign.Start)
                    Cell(session.sets.toString(), 0.6f, style, color)
                    Cell(session.bestSet, 1.4f, style, color)
                    Cell(headline.format(session.values[headline] ?: 0.0), 1.1f, style, color)
                    if (showVolume) Cell(Format.volume(session.values[ProgressMetric.VOLUME] ?: 0.0), 1.1f, style, color)
                }
            }
        }
    }
}

@Composable
private fun TableRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, style: TextStyle, color: Color, align: TextAlign = TextAlign.End) {
    Text(text, style = style, color = color, textAlign = align, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(weight))
}
