package com.janreins.tonnage.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.ExerciseOptions

/** Turns the library into a multi-select list for adding exercises to a workout. */
class PickerMode(
    val selected: Set<String>,
    val onToggle: (Exercise) -> Unit,
    val onDone: () -> Unit,
    val onClose: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    state: ExercisesUiState,
    onQueryChange: (String) -> Unit,
    onMuscleChange: (String?) -> Unit,
    onEquipmentChange: (String?) -> Unit,
    onCustomOnlyChange: (Boolean) -> Unit,
    onExerciseClick: (Exercise) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    picker: PickerMode? = null,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (picker == null) {
                TopAppBar(title = { Text("Exercises") })
            } else {
                TopAppBar(
                    title = { Text("Add exercises") },
                    navigationIcon = {
                        IconButton(onClick = picker.onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    },
                    actions = {
                        IconButton(onClick = onAddClick) { Icon(Icons.Filled.Add, contentDescription = "Create custom exercise") }
                    },
                )
            }
        },
        floatingActionButton = {
            if (picker == null) {
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Filled.Add, contentDescription = "Add custom exercise")
                }
            } else if (picker.selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = picker.onDone,
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    text = { Text(if (picker.selected.size == 1) "Add 1 exercise" else "Add ${picker.selected.size} exercises") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search ${state.totalCount} exercises") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filter.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OptionChip("Muscle", state.filter.muscle, ExerciseOptions.muscles, onMuscleChange)
                OptionChip("Equipment", state.filter.equipment, ExerciseOptions.equipment, onEquipmentChange)
                FilterChip(
                    selected = state.filter.customOnly,
                    onClick = { onCustomOnlyChange(!state.filter.customOnly) },
                    label = { Text("Custom") },
                )
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.exercises.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No exercises match. Tap + to add your own.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.exercises, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise,
                            selected = picker?.selected?.contains(exercise.id),
                            onClick = { if (picker != null) picker.onToggle(exercise) else onExerciseClick(exercise) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, selected: Boolean?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            val muscles = exercise.primaryMuscleList.joinToString(", ") { ExerciseOptions.display(it) }
            val parts = listOf(muscles, ExerciseOptions.display(exercise.equipment)).filter { it.isNotBlank() }
            Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = if (selected != null) {
            {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        } else if (exercise.isCustom) {
            { Text("Custom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    )
}

/** A filter chip that opens a menu of options; picking "All" clears it. */
@Composable
fun OptionChip(label: String, selected: String?, options: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(selected?.let { ExerciseOptions.display(it) } ?: label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelect(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(ExerciseOptions.display(option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
