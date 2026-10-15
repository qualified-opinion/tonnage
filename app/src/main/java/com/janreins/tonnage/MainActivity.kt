package com.janreins.tonnage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janreins.tonnage.ui.components.ComingSoon
import com.janreins.tonnage.ui.exercises.AddExerciseDialog
import com.janreins.tonnage.ui.exercises.ExerciseDetailScreen
import com.janreins.tonnage.ui.exercises.ExercisesScreen
import com.janreins.tonnage.ui.exercises.ExercisesViewModel
import com.janreins.tonnage.ui.state.MainTab
import com.janreins.tonnage.ui.theme.TonnageTheme

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
    var tab by rememberSaveable { mutableStateOf(MainTab.WORKOUT) }
    var openExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddExercise by remember { mutableStateOf(false) }
    val exercisesViewModel: ExercisesViewModel = viewModel()

    val exerciseId = openExerciseId
    if (exerciseId != null) {
        BackHandler { openExerciseId = null }
        val exercise by remember(exerciseId) { exercisesViewModel.observeExercise(exerciseId) }.collectAsState(initial = null)
        ExerciseDetailScreen(
            exercise = exercise,
            onBack = { openExerciseId = null },
            onDelete = {
                exercisesViewModel.deleteCustom(it.id)
                openExerciseId = null
            },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        // Screens draw their own top bars, so only keep the space above the bottom bar.
        val contentModifier = Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize()
        when (tab) {
            MainTab.WORKOUT -> ComingSoon(
                Icons.Filled.FitnessCenter,
                "Workouts",
                "Logging sets, the rest timer and routines arrive in the next update.",
                contentModifier,
            )
            MainTab.HISTORY -> ComingSoon(
                Icons.Filled.History,
                "History",
                "Your finished workouts and imported Hevy history will show here.",
                contentModifier,
            )
            MainTab.EXERCISES -> {
                val state by exercisesViewModel.uiState.collectAsStateWithLifecycle()
                ExercisesScreen(
                    state = state,
                    onQueryChange = exercisesViewModel::setQuery,
                    onMuscleChange = exercisesViewModel::setMuscle,
                    onEquipmentChange = exercisesViewModel::setEquipment,
                    onCustomOnlyChange = exercisesViewModel::setCustomOnly,
                    onExerciseClick = { openExerciseId = it.id },
                    onAddClick = { showAddExercise = true },
                    modifier = contentModifier,
                )
            }
            MainTab.PROGRESS -> ComingSoon(
                Icons.AutoMirrored.Filled.ShowChart,
                "Progress",
                "Personal records, charts and milestones will show here.",
                contentModifier,
            )
            MainTab.MORE -> ComingSoon(
                Icons.Filled.Settings,
                "Settings",
                "Units, backup and Hevy import will live here.",
                contentModifier,
            )
        }
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
}
