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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    WEIGHT("KG", "0", CellKind.DECIMAL),
    REPS("REPS", "0", CellKind.INTEGER),
    DISTANCE("KM", "0", CellKind.DECIMAL),
    TIME("TIME", "0:00", CellKind.DURATION),
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

@Composable
fun ExerciseCard(
    block: ExerciseBlock,
    actions: SetActions,
    onAddSet: () -> Unit,
    onRestChange: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fields = fieldsFor(block.trackingType)
    val labels = WorkoutStats.setLabels(block.sets)
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
                    DropdownMenuItem(text = { Text("Remove exercise") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderText("SET", Modifier.width(40.dp))
            fields.forEach { HeaderText(it.header, Modifier.weight(1f)) }
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        block.sets.forEachIndexed { index, set ->
            SetRow(set, labels[index], fields, actions)
        }
        TextButton(onClick = onAddSet, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add set")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
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

@Composable
private fun SetRow(set: WorkoutSet, label: String, fields: List<Field>, actions: SetActions) {
    val colors = MaterialTheme.colorScheme
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
        fields.forEach { field ->
            SetNumberCell(
                key = set.id,
                initial = when (field) {
                    Field.WEIGHT -> Format.number(set.weightKg)
                    Field.REPS -> set.reps?.toString() ?: ""
                    Field.DISTANCE -> Format.number(set.distanceKm)
                    Field.TIME -> set.durationSec?.let { Format.duration(it * 1000L) } ?: ""
                },
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
            )
        }
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
