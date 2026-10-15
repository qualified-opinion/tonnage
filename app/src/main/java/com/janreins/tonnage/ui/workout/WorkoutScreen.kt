package com.janreins.tonnage.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.janreins.tonnage.data.repository.ActiveWorkout
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.PersonalRecord
import com.janreins.tonnage.domain.PrKind
import com.janreins.tonnage.domain.WorkoutStats
import com.janreins.tonnage.timer.RestTimer
import com.janreins.tonnage.ui.theme.PrGold
import kotlinx.coroutines.delay

/** The Workout tab: a start button, or the workout being logged. */
@Composable
fun WorkoutTab(
    active: ActiveWorkout?,
    rest: RestTimer.State?,
    finishPreview: FinishPreview?,
    viewModel: WorkoutViewModel,
    onAddExercise: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (active == null) {
        StartWorkout(onStart = viewModel::startEmptyWorkout, modifier = modifier)
    } else {
        ActiveWorkoutScreen(active, rest, viewModel, onAddExercise, modifier)
    }
    if (finishPreview != null) {
        FinishDialog(
            preview = finishPreview,
            onSave = { viewModel.finish(onSaved) },
            onDiscard = viewModel::discard,
            onDismiss = viewModel::cancelFinish,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartWorkout(onStart: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Workout") }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    // The rest timer and ongoing-workout notification need this on Android 13+.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    onStart()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start empty workout")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Routines and your previous numbers are coming in the next update.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkoutScreen(
    active: ActiveWorkout,
    rest: RestTimer.State?,
    viewModel: WorkoutViewModel,
    onAddExercise: () -> Unit,
    modifier: Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    var renaming by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val summary = WorkoutStats.summarize(active.blocks, active.workout.startedAt, now)

    val actions = run {
        SetActions(
            onWeight = viewModel::setWeight,
            onReps = viewModel::setReps,
            onDistance = viewModel::setDistance,
            onDuration = viewModel::setDuration,
            onType = viewModel::setType,
            onDelete = viewModel::deleteSet,
            onToggle = { set ->
                active.blocks.firstOrNull { b -> b.sets.any { it.id == set.id } }?.let { viewModel.toggleCompleted(it, set) }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { renaming = true }) {
                        Text(active.workout.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${Format.duration(summary.durationMs)} · ${Format.volume(summary.volumeKg)} · ${summary.completedSets} sets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Button(onClick = viewModel::prepareFinish) { Text("Finish") }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Workout options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename workout") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text("Discard workout", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDiscard = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (rest != null) {
                RestTimerBar(rest, onAdjust = viewModel::adjustRest, onSkip = viewModel::skipRest)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(active.blocks, key = { it.workoutExercise.id }) { block ->
                ExerciseCard(
                    block = block,
                    actions = actions,
                    onAddSet = { viewModel.addSet(block) },
                    onRestChange = { viewModel.setRest(block, it) },
                    onRemove = { viewModel.removeExercise(block) },
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

    if (renaming) {
        RenameDialog(active.workout.title, onDismiss = { renaming = false }, onSave = { viewModel.rename(it); renaming = false })
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("Everything logged in this workout will be deleted.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; viewModel.discard() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun RestTimerBar(rest: RestTimer.State, onAdjust: (Int) -> Unit, onSkip: () -> Unit) {
    var now by remember { mutableLongStateOf(RestTimer.clock()) }
    LaunchedEffect(rest) {
        while (true) {
            now = RestTimer.clock()
            delay(250)
        }
    }
    // The service normally ends the rest; this is a fallback if it isn't running.
    LaunchedEffect(rest) {
        delay(RestTimer.remainingMs(rest) + 3000)
        RestTimer.finish(rest)
    }
    val remaining = RestTimer.remainingMs(rest, now)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { (remaining.toFloat() / rest.totalMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Format.duration(remaining), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        rest.nextSet?.let { "Next: $it" } ?: "Rest",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                OutlinedButton(onClick = { onAdjust(-15) }) { Text("−15") }
                OutlinedButton(onClick = { onAdjust(15) }) { Text("+15") }
                Button(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workout name") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FinishDialog(preview: FinishPreview, onSave: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    val summary = preview.summary
    if (summary.completedSets == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nothing logged yet") },
            text = { Text("Tick off at least one set to save this workout, or discard it.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Keep going") } },
            dismissButton = { TextButton(onClick = onDiscard) { Text("Discard workout") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish workout?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Duration: ${Format.duration(summary.durationMs)}")
                Text("Exercises: ${summary.exerciseCount} · Sets: ${summary.completedSets}")
                Text("Volume: ${Format.volume(summary.volumeKg)}")
                if (summary.incompleteSets > 0) {
                    val sets = if (summary.incompleteSets == 1) "1 unticked set" else "${summary.incompleteSets} unticked sets"
                    Text("$sets will be removed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (preview.records.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("New records", style = MaterialTheme.typography.titleSmall)
                    preview.records.forEach { RecordLine(it) }
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Save workout") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep going") } },
    )
}

@Composable
private fun RecordLine(record: PersonalRecord) {
    fun value(v: Double) = when (record.kind) {
        PrKind.MOST_REPS -> "${v.toInt()} reps"
        else -> "${Format.number(v)} kg"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = PrGold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "${record.exerciseName}: ${record.kind.label.lowercase()} ${value(record.value)} (was ${value(record.previous)})",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
