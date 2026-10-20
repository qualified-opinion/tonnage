package com.janreins.tonnage.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.settings.SettingsStore
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.WorkoutStats

/** What the set row edits for each way of logging an exercise. */
private enum class Field(val header: String, val placeholder: String, val kind: CellKind) {
    WEIGHT("KG", "0", CellKind.WEIGHT),
    REPS("REPS", "0", CellKind.REPS),
    DISTANCE("KM", "0", CellKind.DISTANCE),
    TIME("TIME", "0:00", CellKind.DURATION),
}

/** How a set's stored value for [field] reads in its cell. */
private fun cellText(field: Field, set: WorkoutSet): String = when (field) {
    Field.WEIGHT -> Format.number(set.weightKg)
    Field.REPS -> set.reps?.toString() ?: ""
    Field.DISTANCE -> Format.number(set.distanceKm)
    Field.TIME -> set.durationSec?.let { Format.duration(it * 1000L) } ?: ""
}

private fun fieldsFor(type: TrackingType): List<Field> = when (type) {
    TrackingType.WEIGHT_REPS -> listOf(Field.WEIGHT, Field.REPS)
    TrackingType.BODYWEIGHT_REPS -> listOf(Field.REPS)
    TrackingType.DURATION -> listOf(Field.TIME)
    TrackingType.DISTANCE_DURATION -> listOf(Field.DISTANCE, Field.TIME)
}

class SetActions(
    val onWeight: (WorkoutSet, Double?) -> Unit,
    val onReps: (WorkoutSet, Int?) -> Unit,
    val onDistance: (WorkoutSet, Double?) -> Unit,
    val onDuration: (WorkoutSet, Int?) -> Unit,
    val onType: (WorkoutSet, SetType) -> Unit,
    val onDelete: (WorkoutSet) -> Unit,
    val onToggle: (WorkoutSet) -> Unit,
)

/**
 * One exercise with its sets. [previous] adds the "previous" column (what was done last time,
 * tap to copy); the routine editor leaves it out and hides the tick boxes with [showCheck].
 */
@Composable
fun ExerciseCard(
    block: ExerciseBlock,
    actions: SetActions,
    onAddSet: () -> Unit,
    onRestChange: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    previous: List<WorkoutSet>? = null,
    onUsePrevious: (set: WorkoutSet, previous: WorkoutSet) -> Unit = { _, _ -> },
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    showCheck: Boolean = true,
) {
    val fields = fieldsFor(block.trackingType)
    val labels = WorkoutStats.setLabels(block.sets)
    var confirmRemove by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                block.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RestChip(block.workoutExercise.restSeconds, onRestChange)
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Exercise options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    onMoveUp?.let { move ->
                        DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; move() })
                    }
                    onMoveDown?.let { move ->
                        DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; move() })
                    }
                    DropdownMenuItem(
                        text = { Text("Remove exercise", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            // Only ask when removing would throw away something logged.
                            if (block.hasLoggedSets) {
                                confirmRemove = true
                            } else {
                                onRemove()
                            }
                        },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderText("SET", Modifier.width(40.dp))
            if (previous != null) HeaderText("PREVIOUS", Modifier.weight(PREVIOUS_WEIGHT))
            fields.forEach { HeaderText(it.header, Modifier.weight(1f)) }
            if (showCheck) {
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
        block.sets.forEachIndexed { index, set ->
            key(set.id) {
                SetRow(
                    set = set,
                    label = labels[index],
                    fields = fields,
                    actions = actions,
                    showPrevious = previous != null,
                    previous = previous?.getOrNull(index),
                    trackingType = block.trackingType,
                    onUsePrevious = onUsePrevious,
                    showCheck = showCheck,
                )
            }
        }
        TextButton(onClick = onAddSet, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add set")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
    if (confirmRemove) {
        val sets = if (block.sets.size == 1) "its set" else "its ${block.sets.size} sets"
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${block.name}?") },
            text = { Text("This also deletes $sets, including the numbers you've entered.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun RestChip(restSeconds: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.Timer, contentDescription = "Rest timer", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(Format.restSeconds(restSeconds), style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SettingsStore.REST_OPTIONS.forEach { seconds ->
                DropdownMenuItem(
                    text = { Text(Format.restSeconds(seconds)) },
                    onClick = { open = false; onChange(seconds) },
                )
            }
        }
    }
}

private const val PREVIOUS_WEIGHT = 1.3f

@Composable
private fun SetRow(
    set: WorkoutSet,
    label: String,
    fields: List<Field>,
    actions: SetActions,
    showPrevious: Boolean,
    previous: WorkoutSet?,
    trackingType: TrackingType,
    onUsePrevious: (WorkoutSet, WorkoutSet) -> Unit,
    showCheck: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    // Tapping "previous" pushes its numbers into the cells, even one that is being typed in.
    var fill by remember(set.id) { mutableStateOf<Pair<Int, WorkoutSet>?>(null) }
    // A tick may fill an empty set from last time. Push the saved values even into focused cells.
    LaunchedEffect(set.completed) {
        if (set.completed) fill = ((fill?.first ?: 0) + 1) to set
    }
    val rowColor = if (set.completed) colors.primaryContainer.copy(alpha = 0.45f) else colors.surface
    Row(
        Modifier
            .fillMaxWidth()
            .background(rowColor)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetTypeButton(set, label, actions, Modifier.width(40.dp))
        if (showPrevious) {
            val text = previous?.let { Format.compactSet(it, trackingType) }.orEmpty()
            Text(
                text.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(PREVIOUS_WEIGHT)
                    .then(
                        if (previous != null && text.isNotEmpty()) {
                            Modifier.clickable(onClickLabel = "Use previous values") {
                                fill = ((fill?.first ?: 0) + 1) to previous
                                onUsePrevious(set, previous)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 10.dp),
            )
        }
        fields.forEach { field ->
            SetNumberCell(
                key = set.id,
                initial = cellText(field, set),
                placeholder = field.placeholder,
                kind = field.kind,
                onChange = { text ->
                    when (field) {
                        Field.WEIGHT -> actions.onWeight(set, Format.parseDecimal(text))
                        Field.REPS -> actions.onReps(set, Format.parseInt(text))
                        Field.DISTANCE -> actions.onDistance(set, Format.parseDecimal(text))
                        Field.TIME -> actions.onDuration(set, Format.parseDuration(text))
                    }
                },
                modifier = Modifier.weight(1f),
                fill = fill?.let { (id, from) -> CellFill(id, cellText(field, from)) },
            )
        }
        if (showCheck) {
            Box(
                Modifier
                    .size(44.dp, 40.dp)
                    .background(if (set.completed) colors.primary else colors.surfaceVariant, RoundedCornerShape(8.dp))
                    .clickable { actions.onToggle(set) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (set.completed) "Mark set not done" else "Mark set done",
                    tint = if (set.completed) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Tap the set number to change its type or delete it, as in Hevy. */
@Composable
private fun SetTypeButton(set: WorkoutSet, label: String, actions: SetActions, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val color = when (set.type) {
        SetType.WARMUP -> colors.secondary
        SetType.DROP -> colors.tertiary
        SetType.FAILURE -> colors.error
        SetType.NORMAL -> colors.onSurface
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(40.dp).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SetType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = { open = false; actions.onType(set, type) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete set", color = colors.error) },
                onClick = { open = false; actions.onDelete(set) },
            )
        }
    }
}
