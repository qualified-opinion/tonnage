package com.janreins.tonnage.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.ExerciseOptions
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.domain.WorkoutStats
import com.janreins.tonnage.ui.components.BodyMap

/** Sets of one muscle in a single workout at which the live map shows it fully shaded. */
internal const val LIVE_MAP_FULL_AT = 6

private fun muscleName(muscle: String): String =
    if (muscle == Progress.OTHER_MUSCLE) "Other" else ExerciseOptions.display(muscle)

private fun setCount(sets: Int): String = "$sets ${if (sets == 1) "set" else "sets"}"

/**
 * The muscles worked so far in this workout, on the same front and back body map as Progress. It
 * is recalculated from the workout's sets on every change, so a muscle lights up as soon as one of
 * its sets is ticked. Folding it away is remembered for later workouts.
 */
@Composable
internal fun LiveMuscleMap(blocks: List<ExerciseBlock>, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val muscles = remember(blocks) { WorkoutStats.muscleSets(blocks) }
    val setsByMuscle = remember(muscles) { muscles.associate { it.muscle to it.sets } }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPart by rememberSaveable { mutableStateOf<String?>(null) }
    val summary = if (muscles.isEmpty()) {
        "Tick a set to light up its muscles"
    } else {
        muscles.joinToString(" · ") { "${muscleName(it.muscle)} ${it.sets}" }
    }

    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onExpandedChange(!expanded) }
                .semantics { stateDescription = if (expanded) "Shown" else "Hidden" }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Muscles this workout", style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide body map" else "Show body map",
            )
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BodyMap(
                    setsByMuscle = setsByMuscle,
                    selected = selected,
                    onSelect = { region ->
                        val same = region?.muscle == selected && region?.part == selectedPart
                        selected = if (same) null else region?.muscle
                        selectedPart = if (same) null else region?.part
                    },
                    description = if (muscles.isEmpty()) {
                        "Body map with no muscles worked yet"
                    } else {
                        "Body map of sets per muscle this workout: " + muscles.joinToString { "${muscleName(it.muscle)} ${setCount(it.sets)}" }
                    },
                    fullAt = LIVE_MAP_FULL_AT,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
                Text(
                    selected?.let { muscle ->
                        val name = muscleName(muscle)
                        val part = selectedPart?.takeUnless { it.equals(name, ignoreCase = true) }
                        "${part?.let { "$it · " }.orEmpty()}$name: ${setCount(setsByMuscle[muscle] ?: 0)}"
                    } ?: "Working sets, warm-ups left out. Darkest at $LIVE_MAP_FULL_AT sets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
