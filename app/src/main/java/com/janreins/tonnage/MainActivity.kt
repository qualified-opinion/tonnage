package com.janreins.tonnage

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.settings.SettingsStore
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.ui.exercises.AddExerciseDialog
import com.janreins.tonnage.ui.exercises.ExerciseDetailScreen
import com.janreins.tonnage.ui.exercises.ExercisesScreen
import com.janreins.tonnage.ui.exercises.ExercisesViewModel
import com.janreins.tonnage.ui.exercises.PickerMode
import com.janreins.tonnage.ui.history.HistoryScreen
import com.janreins.tonnage.ui.history.HistoryViewModel
import com.janreins.tonnage.ui.routines.RoutineEditorScreen
import com.janreins.tonnage.ui.routines.RoutinesViewModel
import com.janreins.tonnage.ui.components.ComingSoon
import com.janreins.tonnage.ui.settings.SettingsScreen
import com.janreins.tonnage.ui.state.MainTab
import com.janreins.tonnage.ui.theme.TonnageTheme
import com.janreins.tonnage.ui.workout.RoutineListActions
import com.janreins.tonnage.ui.workout.WorkoutTab
import com.janreins.tonnage.ui.workout.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PickerTarget { WORKOUT, ROUTINE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TonnageTheme {
                TonnageApp()
            }
        }
    }
}

@Composable
private fun TonnageApp() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.WORKOUT) }
    var openExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    // Which screen the exercise picker adds to, or null when it's closed.
    var pickerTarget by rememberSaveable { mutableStateOf<PickerTarget?>(null) }
    var pickerSelection by remember { mutableStateOf(listOf<String>()) }
    var showAddExercise by remember { mutableStateOf(false) }

    val exercisesViewModel: ExercisesViewModel = viewModel()
    val workoutViewModel: WorkoutViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    val routinesViewModel: RoutinesViewModel = viewModel()
    val settings = remember { SettingsStore.get(context) }

    val activeWorkout by workoutViewModel.active.collectAsStateWithLifecycle()
    val pendingResume by workoutViewModel.pendingResume.collectAsStateWithLifecycle()
    val finishPreview by workoutViewModel.finishPreview.collectAsStateWithLifecycle()
    val rest by workoutViewModel.restTimer.collectAsStateWithLifecycle()
    val exercisesState by exercisesViewModel.uiState.collectAsStateWithLifecycle()
    val previous by workoutViewModel.previous.collectAsStateWithLifecycle()
    val routines by routinesViewModel.routines.collectAsStateWithLifecycle()
    val routineEditor by routinesViewModel.editor.collectAsStateWithLifecycle()
    val routineBlocks by routinesViewModel.editorBlocks.collectAsStateWithLifecycle()

    val exerciseId = openExerciseId
    val target = pickerTarget
    val editor = routineEditor
    when {
        exerciseId != null -> {
            BackHandler { openExerciseId = null }
            val exercise by remember(exerciseId) { exercisesViewModel.observeExercise(exerciseId) }.collectAsState(initial = null)
            ExerciseDetailScreen(
                exercise = exercise,
                onBack = { openExerciseId = null },
                onDelete = {
                    exercisesViewModel.deleteCustom(it.id) { deleted ->
                        if (deleted) {
                            openExerciseId = null
                        } else {
                            Toast.makeText(context, "This exercise is used in a workout or routine, so it can't be deleted.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }
        target != null -> {
            val close = { pickerTarget = null; pickerSelection = emptyList() }
            BackHandler { close() }
            ExercisesScreen(
                state = exercisesState,
                onQueryChange = exercisesViewModel::setQuery,
                onMuscleChange = exercisesViewModel::setMuscle,
                onEquipmentChange = exercisesViewModel::setEquipment,
                onCustomOnlyChange = exercisesViewModel::setCustomOnly,
                onExerciseClick = {},
                onAddClick = { showAddExercise = true },
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                picker = PickerMode(
                    selected = pickerSelection.toSet(),
                    onToggle = { exercise ->
                        pickerSelection = if (exercise.id in pickerSelection) pickerSelection - exercise.id else pickerSelection + exercise.id
                    },
                    onDone = {
                        when (target) {
                            PickerTarget.WORKOUT -> workoutViewModel.addExercises(pickerSelection)
                            PickerTarget.ROUTINE -> routinesViewModel.addExercises(pickerSelection)
                        }
                        close()
                    },
                    onClose = close,
                ),
            )
        }
        editor != null -> RoutineEditorScreen(
            editor = editor,
            blocks = routineBlocks,
            viewModel = routinesViewModel,
            onAddExercise = { pickerTarget = PickerTarget.ROUTINE },
            modifier = Modifier.fillMaxSize(),
        )
        else -> MainTabs(
            tab = tab,
            onTabChange = { tab = it },
            content = { modifier ->
                when (tab) {
                    MainTab.WORKOUT -> WorkoutTab(
                        active = activeWorkout,
                        previous = previous,
                        rest = rest,
                        finishPreview = finishPreview,
                        routines = routines,
                        routineActions = RoutineListActions(
                            onStart = workoutViewModel::startRoutine,
                            onNew = routinesViewModel::newRoutine,
                            onEdit = routinesViewModel::edit,
                            onDuplicate = routinesViewModel::duplicate,
                            onDelete = routinesViewModel::delete,
                        ),
                        viewModel = workoutViewModel,
                        onAddExercise = { pickerTarget = PickerTarget.WORKOUT },
                        onSaved = { tab = MainTab.HISTORY },
                        modifier = modifier,
                    )
                    MainTab.HISTORY -> {
                        val workouts by historyViewModel.workouts.collectAsStateWithLifecycle()
                        HistoryScreen(workouts, modifier)
                    }
                    MainTab.EXERCISES -> ExercisesScreen(
                        state = exercisesState,
                        onQueryChange = exercisesViewModel::setQuery,
                        onMuscleChange = exercisesViewModel::setMuscle,
                        onEquipmentChange = exercisesViewModel::setEquipment,
                        onCustomOnlyChange = exercisesViewModel::setCustomOnly,
                        onExerciseClick = { openExerciseId = it.id },
                        onAddClick = { showAddExercise = true },
                        modifier = modifier,
                    )
                    MainTab.PROGRESS -> ComingSoon(
                        MainTab.PROGRESS.icon,
                        "Progress",
                        "Personal records, charts and milestones will show here.",
                        modifier,
                    )
                    MainTab.MORE -> {
                        val restSeconds by settings.defaultRestSeconds.collectAsStateWithLifecycle()
                        SettingsScreen(restSeconds, settings::setDefaultRestSeconds, modifier)
                    }
                }
            },
        )
    }

    if (showAddExercise) {
        AddExerciseDialog(
            onDismiss = { showAddExercise = false },
            onSave = { name, muscle, equipment, trackingType ->
                exercisesViewModel.addCustom(name, muscle, equipment, trackingType)
                showAddExercise = false
            },
        )
    }

    pendingResume?.let { workout ->
        ResumeDialog(
            workout = workout,
            onResume = {
                workoutViewModel.resumePending()
                tab = MainTab.WORKOUT
            },
            onDiscard = workoutViewModel::discardPending,
        )
    }
}

@Composable
private fun MainTabs(tab: MainTab, onTabChange: (MainTab) -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { onTabChange(item) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        // Screens draw their own top bars, so only keep the space above the bottom bar.
        content(Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize())
    }
}

@Composable
private fun ResumeDialog(workout: Workout, onResume: () -> Unit, onDiscard: () -> Unit) {
    val started = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(workout.startedAt))
    val elapsed = Format.duration(System.currentTimeMillis() - workout.startedAt)
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Unfinished workout") },
        text = { Text("\"${workout.title}\" was started $started ($elapsed ago) and wasn't finished. Pick up where you left off?") },
        confirmButton = { TextButton(onClick = onResume) { Text("Resume") } },
        dismissButton = { TextButton(onClick = onDiscard) { Text("Discard") } },
    )
}
