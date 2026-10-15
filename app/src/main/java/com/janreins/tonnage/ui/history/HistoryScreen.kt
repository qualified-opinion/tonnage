package com.janreins.tonnage.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.WorkoutSummary
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.ui.components.ComingSoon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(workouts: List<WorkoutSummary>?, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("History") }) },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when {
            workouts == null -> Box(content, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            workouts.isEmpty() -> ComingSoon(
                Icons.Filled.History,
                "No workouts yet",
                "Finished workouts show up here. Your Hevy history can be imported in a later update.",
                content,
            )
            else -> LazyColumn(
                content,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(workouts, key = { it.id }) { WorkoutCard(it) }
            }
        }
    }
}

@Composable
private fun WorkoutCard(workout: WorkoutSummary) {
    val date = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault()).format(Date(workout.startedAt))
    val duration = workout.endedAt?.let { Format.duration(it - workout.startedAt) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(workout.title, style = MaterialTheme.typography.titleMedium)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val exercises = if (workout.exerciseCount == 1) "1 exercise" else "${workout.exerciseCount} exercises"
            val parts = listOfNotNull(duration, exercises, "${workout.setCount} sets", Format.volume(workout.volumeKg))
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
