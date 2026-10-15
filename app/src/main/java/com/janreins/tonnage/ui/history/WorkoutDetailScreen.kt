package com.janreins.tonnage.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.repository.ActiveWorkout
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.PersonalRecord
import com.janreins.tonnage.domain.WorkoutStats
import com.janreins.tonnage.ui.theme.PrGold
import com.janreins.tonnage.ui.workout.ExerciseCard
import com.janreins.tonnage.ui.workout.RecordLine
import com.janreins.tonnage.ui.workout.SetActions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A finished workout from history: every set and its records, with edit, delete and save as routine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workout: ActiveWorkout?,
    records: List<PersonalRecord>,
    editing: Boolean,
    viewModel: HistoryViewModel,
    onAddExercise: () -> Unit,
    onSaveAsRoutine: (ActiveWorkout) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { if (editing) viewModel.finishEditing() else viewModel.close() }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (!editing) {
                        IconButton(onClick = viewModel::close) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    Text(
                        if (editing) "Edit workout" else workout?.workout?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (workout != null && editing) {
                        Button(onClick = viewModel::finishEditing, modifier = Modifier.padding(end = 8.dp)) { Text("Done") }
                    } else if (workout != null) {
                        IconButton(onClick = viewModel::startEditing) { Icon(Icons.Filled.Edit, contentDescription = "Edit workout") }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Workout options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Save as routine") }, onClick = { menu = false; onSaveAsRoutine(workout) })
                                DropdownMenuItem(
                                    text = { Text("Delete workout", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when {
            workout == null -> Box(content, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            editing -> EditContent(workout, viewModel, onAddExercise, content)
            else -> ViewContent(workout, records, content)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this workout?") },
            text = { Text("It's removed from your history, and records are worked out again without it.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ViewContent(workout: ActiveWorkout, records: List<PersonalRecord>, modifier: Modifier) {
    val endedAt = workout.workout.endedAt ?: workout.workout.startedAt
    val summary = WorkoutStats.summarize(workout.blocks, workout.workout.startedAt, endedAt)
    val date = SimpleDateFormat("EEEE d MMMM yyyy · HH:mm", Locale.getDefault()).format(Date(workout.workout.startedAt))
    val recordSetIds = records.map { it.setId }.toSet()

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${Format.duration(summary.durationMs)} · ${summary.completedSets} sets · ${Format.volume(summary.volumeKg)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (records.isNotEmpty()) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Records", style = MaterialTheme.typography.titleSmall)
                        records.forEach { RecordLine(it) }
                    }
                }
            }
        }
        items(workout.blocks, key = { it.workoutExercise.id }) { block ->
            ExerciseSummary(block, recordSetIds)
        }
    }
}

@Composable
private fun ExerciseSummary(block: ExerciseBlock, recordSetIds: Set<Long>) {
    val labels = WorkoutStats.setLabels(block.sets)
    Column(Modifier.fillMaxWidth()) {
        Text(block.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        block.sets.forEachIndexed { index, set ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(labels[index], fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                Text(Format.setTarget(set, block.trackingType).ifEmpty { "—" }, modifier = Modifier.weight(1f))
                if (set.id in recordSetIds) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = "Record", tint = PrGold, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
}

@Composable
private fun EditContent(workout: ActiveWorkout, viewModel: HistoryViewModel, onAddExercise: () -> Unit, modifier: Modifier) {
    val actions = SetActions(
        onWeight = viewModel::setWeight,
        onReps = viewModel::setReps,
        onDistance = viewModel::setDistance,
        onDuration = viewModel::setDuration,
        onType = viewModel::setType,
        onDelete = viewModel::deleteSet,
        onToggle = {},
    )
    var title by remember(workout.workout.id) { mutableStateOf(workout.workout.title) }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; viewModel.rename(it) },
                label = { Text("Workout name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                "Changes save as you type. Blank sets are removed when you tap Done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        itemsIndexed(workout.blocks, key = { _, block -> block.workoutExercise.id }) { index, block ->
            ExerciseCard(
                block = block,
                actions = actions,
                onAddSet = { viewModel.addSet(block) },
                onRestChange = { viewModel.setRest(block, it) },
                onRemove = { viewModel.removeExercise(block) },
                onMoveUp = if (index > 0) ({ viewModel.moveExercise(block, -1) }) else null,
                onMoveDown = if (index < workout.blocks.lastIndex) ({ viewModel.moveExercise(block, 1) }) else null,
                showCheck = false,
            )
        }
        item {
            FilledTonalButton(
                onClick = onAddExercise,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add exercise")
            }
        }
    }
}
