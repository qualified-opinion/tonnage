package com.janreins.tonnage.ui.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.ui.workout.ExerciseCard
import com.janreins.tonnage.ui.workout.SetActions

/** Full-screen editor for a routine: name, exercises and the planned sets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    editor: RoutineEditor,
    blocks: List<ExerciseBlock>,
    viewModel: RoutinesViewModel,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClose by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (editor.hasChanges) {
            confirmClose = true
        } else {
            viewModel.close()
        }
    }
    BackHandler { close() }

    val actions = SetActions(
        onWeight = viewModel::setWeight,
        onReps = viewModel::setReps,
        onDistance = viewModel::setDistance,
        onDuration = viewModel::setDuration,
        onType = viewModel::setType,
        onDelete = viewModel::deleteSet,
        onToggle = {},
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                },
                title = { Text(if (editor.original.id == 0L) "New routine" else "Edit routine") },
                actions = {
                    Button(onClick = viewModel::save, enabled = editor.draft.canSave, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = editor.draft.name,
                    onValueChange = viewModel::rename,
                    label = { Text("Routine name") },
                    placeholder = { Text("e.g. Push A") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "The numbers you enter here are filled in each time you start this routine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            itemsIndexed(blocks, key = { _, block -> block.workoutExercise.id }) { index, block ->
                ExerciseCard(
                    block = block,
                    actions = actions,
                    onAddSet = { viewModel.addSet(block) },
                    onRestChange = { viewModel.setRest(block, it) },
                    onRemove = { viewModel.removeExercise(block) },
                    onMoveUp = if (index > 0) ({ viewModel.moveExercise(block, -1) }) else null,
                    onMoveDown = if (index < blocks.lastIndex) ({ viewModel.moveExercise(block, 1) }) else null,
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

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this routine won't be saved.") },
            confirmButton = { TextButton(onClick = { confirmClose = false; viewModel.close() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } },
        )
    }
}
