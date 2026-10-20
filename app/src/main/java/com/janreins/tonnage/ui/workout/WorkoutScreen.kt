package com.janreins.tonnage.ui.workout

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.tonnage.data.model.RoutineSummary
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.repository.ActiveWorkout
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.PersonalRecord
import com.janreins.tonnage.domain.PrKind
import com.janreins.tonnage.domain.WorkoutStats
import com.janreins.tonnage.timer.RestTimer
import com.janreins.tonnage.ui.theme.PrGold
import kotlinx.coroutines.delay

/** What the routine list on the Workout tab can do. */
class RoutineListActions(
    val onStart: (Long) -> Unit,
    val onNew: () -> Unit,
    val onEdit: (Long) -> Unit,
    val onDuplicate: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
)

/** The Workout tab: quick start and routines, or the workout being logged. */
@Composable
fun WorkoutTab(
    active: ActiveWorkout?,
    previous: Map<String, List<WorkoutSet>>,
    rest: RestTimer.State?,
    finishPreview: FinishPreview?,
    routines: List<RoutineSummary>,
    routineActions: RoutineListActions,
    viewModel: WorkoutViewModel,
    onAddExercise: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (active == null) {
        StartWorkout(routines, onStartEmpty = viewModel::startEmptyWorkout, actions = routineActions, modifier = modifier)
    } else {
        ActiveWorkoutScreen(active, previous, rest, viewModel, onAddExercise, modifier)
    }
    if (finishPreview != null) {
        FinishDialog(
            preview = finishPreview,
            onSave = { viewModel.finish(onSaved) },
            onCompleteFilledAndSave = { viewModel.completeFilledAndFinish(onSaved) },
            onDiscard = viewModel::discard,
            onDismiss = viewModel::cancelFinish,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartWorkout(
    routines: List<RoutineSummary>,
    onStartEmpty: () -> Unit,
    actions: RoutineListActions,
    modifier: Modifier,
) {
    var deleting by remember { mutableStateOf<RoutineSummary?>(null) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Workout") }) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    onClick = onStartEmpty,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start empty workout")
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Routines", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = actions.onNew) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New routine")
                    }
                }
            }
            if (routines.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No routines yet. Save the workouts you repeat, like Push A or Legs, and start them with one tap.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(routines, key = { it.routine.id }) { summary ->
                RoutineCard(
                    summary = summary,
                    onStart = { actions.onStart(summary.routine.id) },
                    onEdit = { actions.onEdit(summary.routine.id) },
                    onDuplicate = { actions.onDuplicate(summary.routine.id) },
                    onDelete = { deleting = summary },
                )
            }
        }
    }

    deleting?.let { summary ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${summary.routine.name}\"?") },
            text = { Text("The routine is removed. Workouts you already logged with it stay in your history.") },
            confirmButton = {
                TextButton(onClick = { deleting = null; actions.onDelete(summary.routine.id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineCard(
    summary: RoutineSummary,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Routine options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            Text(
                summary.exerciseNames.joinToString(", ").ifEmpty { "No exercises" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onStart, modifier = Modifier.padding(end = 12.dp).fillMaxWidth()) {
                Text("Start routine")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkoutScreen(
    active: ActiveWorkout,
    previous: Map<String, List<WorkoutSet>>,
    rest: RestTimer.State?,
    viewModel: WorkoutViewModel,
    onAddExercise: () -> Unit,
    modifier: Modifier,
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // Keep the screen on while logging, so it doesn't lock between sets.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val currentActive by rememberUpdatedState(active)
    val actions = remember(viewModel) {
        SetActions(
            onWeight = viewModel::setWeight,
            onReps = viewModel::setReps,
            onDistance = viewModel::setDistance,
            onDuration = viewModel::setDuration,
            onType = viewModel::setType,
            onDelete = viewModel::deleteSet,
            onToggle = { set ->
                currentActive.blocks.firstOrNull { b -> b.sets.any { it.id == set.id } }?.let { viewModel.toggleCompleted(it, set) }
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
                        WorkoutSubtitle(active)
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
                RestTimerBar(rest, onFinish = viewModel::finishRest, onAdjust = viewModel::adjustRest, onSkip = viewModel::skipRest)
            }
        },
    ) { padding ->
        val notificationsAvailable by viewModel.notificationsAvailable.collectAsStateWithLifecycle()
        val context = LocalContext.current
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                RestNotificationNotice(notificationsAvailable) {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            itemsIndexed(active.blocks, key = { _, block -> block.workoutExercise.id }) { index, block ->
                ExerciseCard(
                    block = block,
                    actions = actions,
                    onAddSet = { viewModel.addSet(block) },
                    onRestChange = { viewModel.setRest(block, it) },
                    onRemove = { viewModel.removeExercise(block) },
                    previous = previous[block.workoutExercise.exerciseId].orEmpty(),
                    onUsePrevious = viewModel::usePrevious,
                    onMoveUp = if (index > 0) ({ viewModel.moveExercise(block, -1) }) else null,
                    onMoveDown = if (index < active.blocks.lastIndex) ({ viewModel.moveExercise(block, 1) }) else null,
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

/** Only the subtitle reads the ticking clock; exercise cards do not depend on it. */
@Composable
private fun WorkoutSubtitle(active: ActiveWorkout) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val summary = WorkoutStats.summarize(active.blocks, active.workout.startedAt, now)
    Text(
        "${Format.duration(summary.durationMs)} · ${Format.volume(summary.volumeKg)} · ${summary.completedSets} sets",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RestTimerBar(rest: RestTimer.State, onFinish: (RestTimer.State) -> Unit, onAdjust: (Int) -> Unit, onSkip: () -> Unit) {
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
        onFinish(rest)
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
private fun FinishDialog(
    preview: FinishPreview,
    onSave: () -> Unit,
    onCompleteFilledAndSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val summary = preview.summary
    val filled = summary.filledIncompleteSets
    val filledSets = if (filled == 1) "1 filled set" else "$filled filled sets"
    var confirmDiscard by remember { mutableStateOf(false) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Discard this workout?") },
            text = { Text("$filledSets will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = onDiscard) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Keep going") } },
        )
        return
    }
    if (summary.completedSets == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (filled > 0) "Nothing ticked yet" else "Nothing logged yet") },
            text = {
                Text(
                    if (filled > 0) {
                        "You have $filledSets that aren't ticked. Tick them and save, or keep going."
                    } else {
                        "Tick off at least one set to save this workout, or discard it." +
                            if (summary.plannedIncompleteSets > 0) " Untouched planned sets aren't logged." else ""
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Keep going") }
            },
            dismissButton = {
                Row {
                    // Discarding filled sets always needs a second, explicit confirmation.
                    TextButton(onClick = { if (filled > 0) confirmDiscard = true else onDiscard() }) {
                        Text("Discard workout", color = MaterialTheme.colorScheme.error)
                    }
                    if (filled > 0) TextButton(onClick = onCompleteFilledAndSave) { Text("Tick them and save") }
                }
            },
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
                if (filled > 0) {
                    val verb = if (filled == 1) "isn't" else "aren't"
                    Text("$filledSets $verb ticked and won't be saved unless you tick them.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onCompleteFilledAndSave) { Text("Tick them and save") }
                }
                if (summary.plannedIncompleteSets > 0) {
                    Text("${summary.plannedIncompleteSets} untouched planned sets won't be saved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val blank = summary.incompleteSets - filled - summary.plannedIncompleteSets
                if (blank > 0) {
                    val sets = if (blank == 1) "1 empty set" else "$blank empty sets"
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
fun RecordLine(record: PersonalRecord) {
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

@Composable
internal fun RestNotificationNotice(enabled: Boolean, onSettings: () -> Unit) {
    if (!enabled) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Rest notifications are off", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onSettings) { Text("Notification settings") }
        }
    }
}
