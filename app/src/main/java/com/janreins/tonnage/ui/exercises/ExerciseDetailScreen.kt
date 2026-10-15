package com.janreins.tonnage.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.ExerciseOptions
import com.janreins.tonnage.domain.ExerciseSession
import com.janreins.tonnage.ui.progress.ExerciseProgressSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exercise: Exercise?,
    progress: List<ExerciseSession>,
    onBack: () -> Unit,
    onDelete: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (exercise?.isCustom == true) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete exercise")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (exercise == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (progress.isNotEmpty()) {
                item { ExerciseProgressSection(progress, exercise.trackingType) }
            }
            item { Fact("Primary muscles", exercise.primaryMuscleList.joinToString(", ") { ExerciseOptions.display(it) }) }
            if (exercise.secondaryMuscleList.isNotEmpty()) {
                item { Fact("Secondary muscles", exercise.secondaryMuscleList.joinToString(", ") { ExerciseOptions.display(it) }) }
            }
            item { Fact("Equipment", ExerciseOptions.display(exercise.equipment)) }
            item { Fact("Logged as", exercise.trackingType.label) }
            if (exercise.instructionSteps.isNotEmpty()) {
                item { Text("How to", style = MaterialTheme.typography.titleMedium) }
                itemsIndexed(exercise.instructionSteps) { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (confirmDelete && exercise != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${exercise.name}?") },
            text = { Text("This removes the custom exercise from your library.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(exercise) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Fact(label: String, value: String) {
    androidx.compose.foundation.layout.Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
