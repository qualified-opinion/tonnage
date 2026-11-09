package com.janreins.tonnage.ui.history

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.WorkoutSummary
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.PersonalRecord
import com.janreins.tonnage.ui.components.ComingSoon
import com.janreins.tonnage.ui.theme.PrGold
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    workouts: List<WorkoutSummary>?,
    records: Map<Long, List<PersonalRecord>>,
    onOpen: (WorkoutSummary) -> Unit,
    modifier: Modifier = Modifier,
    backupError: String? = null,
    onOpenSettings: () -> Unit = {},
    exerciseNames: Map<Long, List<String>> = emptyMap(),
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (!workouts.isNullOrEmpty()) {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(
                                if (searching) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (searching) "Close search" else "Search workouts",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            backupError?.let { BackupWarning(it, onOpenSettings) }
            if (searching && !workouts.isNullOrEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Workout or exercise") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            val content = Modifier.weight(1f).fillMaxWidth()
            when {
                workouts == null -> Box(content, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                workouts.isEmpty() -> ComingSoon(
                    Icons.Filled.History,
                    "No workouts yet",
                    "Finished workouts show up here. To bring in your Hevy history, use Import from Hevy on the Settings tab.",
                    content,
                )
                else -> {
                    val groups = remember(workouts, exerciseNames, query) {
                        HistoryGroups.group(workouts, exerciseNames, query, ZoneId.systemDefault())
                    }
                    if (groups.isEmpty()) {
                        Box(content.padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No workouts match \"$query\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            content,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            groups.forEach { group ->
                                item(key = "month-${group.month}") { MonthHeading(group) }
                                items(group.workouts, key = { it.id }) { workout ->
                                    WorkoutCard(
                                        workout,
                                        records[workout.id].orEmpty().size,
                                        exerciseNames[workout.id].orEmpty(),
                                        onClick = { onOpen(workout) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "November 2026" with the month's workout count and volume. */
@Composable
private fun MonthHeading(group: MonthGroup) {
    val locale = LocalLocale.current.platformLocale
    val count = group.workouts.size
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            group.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${if (count == 1) "1 workout" else "$count workouts"} · ${Format.volume(group.volumeKg)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown while the automatic backup is failing, so it can't go unnoticed for weeks. */
@Composable
private fun BackupWarning(error: String, onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Automatic backup isn't working", style = MaterialTheme.typography.titleSmall)
                Text("The last one failed: $error", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpenSettings) { Text("Fix") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutCard(workout: WorkoutSummary, recordCount: Int, exerciseNames: List<String>, onClick: () -> Unit) {
    val date = SimpleDateFormat("EEE d MMM yyyy · HH:mm", LocalLocale.current.platformLocale).format(Date(workout.startedAt))
    val duration = workout.endedAt?.let { Format.duration(it - workout.startedAt) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(workout.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (recordCount > 0) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = PrGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (recordCount == 1) "1 record" else "$recordCount records",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (exerciseNames.isNotEmpty()) {
                Text(
                    exerciseNames.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val exercises = if (workout.exerciseCount == 1) "1 exercise" else "${workout.exerciseCount} exercises"
            val parts = listOfNotNull(duration, exercises, "${workout.setCount} sets", Format.volume(workout.volumeKg))
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
