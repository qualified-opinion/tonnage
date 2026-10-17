package com.janreins.tonnage

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.janreins.tonnage.data.settings.ThemeMode
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.ui.data.DataViewModel
import com.janreins.tonnage.ui.data.ImportScreen
import com.janreins.tonnage.ui.exercises.AddExerciseDialog
import com.janreins.tonnage.ui.exercises.ExerciseDetailScreen
import com.janreins.tonnage.ui.exercises.ExercisesScreen
import com.janreins.tonnage.ui.exercises.ExercisesViewModel
import com.janreins.tonnage.ui.exercises.PickerMode
import com.janreins.tonnage.ui.history.HistoryScreen
import com.janreins.tonnage.ui.history.HistoryViewModel
import com.janreins.tonnage.ui.history.WorkoutDetailScreen
import com.janreins.tonnage.ui.progress.ProgressScreen
import com.janreins.tonnage.ui.progress.ProgressViewModel
import com.janreins.tonnage.ui.routines.RoutineEditorScreen
import com.janreins.tonnage.ui.routines.RoutinesViewModel
import com.janreins.tonnage.ui.settings.DataActions
import com.janreins.tonnage.ui.settings.SettingsScreen
import com.janreins.tonnage.ui.state.MainTab
import com.janreins.tonnage.ui.theme.TonnageTheme
import com.janreins.tonnage.ui.workout.RoutineListActions
import com.janreins.tonnage.ui.workout.WorkoutTab
import com.janreins.tonnage.ui.workout.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PickerTarget { WORKOUT, ROUTINE, EDITED_WORKOUT, IMPORT_MATCH }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsStore.get(this)
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app's theme, not only the phone's.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            TonnageTheme(darkTheme = darkTheme) {
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
    val dataViewModel: DataViewModel = viewModel()
    val progressViewModel: ProgressViewModel = viewModel()
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
    val openWorkoutId by historyViewModel.openId.collectAsStateWithLifecycle()
    val openWorkout by historyViewModel.opened.collectAsStateWithLifecycle()
    val editingWorkout by historyViewModel.editing.collectAsStateWithLifecycle()
    val records by historyViewModel.records.collectAsStateWithLifecycle()
    val importState by dataViewModel.importState.collectAsStateWithLifecycle()
    val backupState by dataViewModel.backupState.collectAsStateWithLifecycle()
    val library by dataViewModel.library.collectAsStateWithLifecycle()

    val exerciseId = openExerciseId
    val target = pickerTarget
    val editor = routineEditor
    val importing = importState
    when {
        exerciseId != null -> {
            BackHandler { openExerciseId = null }
            val exercise by remember(exerciseId) { exercisesViewModel.observeExercise(exerciseId) }.collectAsState(initial = null)
            val sessions by remember(exerciseId) { progressViewModel.sessions(exerciseId) }.collectAsState(initial = emptyList())
            ExerciseDetailScreen(
                exercise = exercise,
                progress = sessions,
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
                        if (target == PickerTarget.IMPORT_MATCH) {
                            // Matching a Hevy name takes one exercise, so the first tap decides.
                            dataViewModel.picked(exercise)
                            close()
                        } else {
                            pickerSelection = if (exercise.id in pickerSelection) pickerSelection - exercise.id else pickerSelection + exercise.id
                        }
                    },
                    onDone = {
                        when (target) {
                            PickerTarget.WORKOUT -> workoutViewModel.addExercises(pickerSelection)
                            PickerTarget.ROUTINE -> routinesViewModel.addExercises(pickerSelection)
                            PickerTarget.EDITED_WORKOUT -> historyViewModel.addExercises(pickerSelection)
                            PickerTarget.IMPORT_MATCH -> Unit
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
        importing != null -> ImportScreen(
            state = importing,
            library = library,
            viewModel = dataViewModel,
            onSearch = { hevyName ->
                dataViewModel.pickFor(hevyName)
                pickerTarget = PickerTarget.IMPORT_MATCH
            },
            onViewHistory = {
                dataViewModel.closeImport()
                tab = MainTab.HISTORY
            },
            modifier = Modifier.fillMaxSize(),
        )
        openWorkoutId != null -> WorkoutDetailScreen(
            workout = openWorkout?.takeIf { it.workout.id == openWorkoutId },
            records = openWorkoutId?.let { records[it] }.orEmpty(),
            editing = editingWorkout,
            viewModel = historyViewModel,
            onAddExercise = { pickerTarget = PickerTarget.EDITED_WORKOUT },
            onSaveAsRoutine = { routinesViewModel.newFromWorkout(it.workout.title, it.blocks) },
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
                        HistoryScreen(workouts, records, onOpen = { historyViewModel.open(it.id) }, modifier = modifier)
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
                    MainTab.PROGRESS -> {
                        val overview by progressViewModel.overview.collectAsStateWithLifecycle()
                        ProgressScreen(overview, progressViewModel, onOpenExercise = { openExerciseId = it }, modifier = modifier)
                    }
                    MainTab.MORE -> {
                        val restSeconds by settings.defaultRestSeconds.collectAsStateWithLifecycle()
                        val themeMode by settings.themeMode.collectAsStateWithLifecycle()
                        SettingsScreen(
                            defaultRestSeconds = restSeconds,
                            onDefaultRestChange = settings::setDefaultRestSeconds,
                            themeMode = themeMode,
                            onThemeModeChange = settings::setThemeMode,
                            backupState = backupState,
                            data = DataActions(
                                onImportHevy = dataViewModel::startImport,
                                onSaveBackup = dataViewModel::saveBackup,
                                onOpenBackup = dataViewModel::openBackup,
                                onConfirmRestore = dataViewModel::confirmRestore,
                                onDismissBackup = dataViewModel::dismissBackup,
                            ),
                            modifier = modifier,
                        )
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
    var confirmDiscard by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Unfinished workout") },
        text = { Text("\"${workout.title}\" was started $started ($elapsed ago) and wasn't finished. Pick up where you left off?") },
        confirmButton = { TextButton(onClick = onResume) { Text("Resume") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = true }) { Text("Discard") } },
    )
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard \"${workout.title}\"?") },
            text = { Text("Everything logged in this workout will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDiscard() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep it") } },
        )
    }
}
