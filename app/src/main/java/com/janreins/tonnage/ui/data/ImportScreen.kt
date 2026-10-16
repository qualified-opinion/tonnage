package com.janreins.tonnage.ui.data

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.hevy.ExerciseMatch
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.repository.ExerciseChoice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The Hevy import: review unmatched exercises, preview, import, done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    state: ImportState,
    library: Map<String, Exercise>,
    viewModel: DataViewModel,
    onSearch: (hevyName: String) -> Unit,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reviewing = state is ImportState.Ready && state.reviewing
    BackHandler {
        if (reviewing) viewModel.showReview(false) else viewModel.closeImport()
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (state !is ImportState.Importing) {
                        IconButton(onClick = viewModel::closeImport) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    }
                },
                title = { Text(if (reviewing) "Check exercises" else "Import from Hevy") },
            )
        },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when (state) {
            ImportState.Reading -> Busy("Reading your Hevy export…", content)
            ImportState.Importing -> Busy("Importing…", content)
            is ImportState.Failed -> Message(Icons.Filled.ErrorOutline, "Couldn't import", state.message, "Close", viewModel::closeImport, content)
            is ImportState.Done -> {
                val r = state.result
                val lines = buildList {
                    add("Imported ${plural(r.workouts, "workout")} with ${plural(r.sets, "set")}.")
                    if (r.createdExercises > 0) add("Added ${plural(r.createdExercises, "custom exercise")}.")
                    if (r.skipped > 0) add("Skipped ${plural(r.skipped, "workout")} already imported.")
                }
                Message(Icons.Filled.CheckCircle, "Import complete", lines.joinToString(" "), "View history", onViewHistory, content)
            }
            is ImportState.Ready -> if (state.reviewing) {
                Review(state, library, viewModel, onSearch, content)
            } else {
                Preview(state, viewModel, content)
            }
        }
    }
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "%,d ${word}s".format(Locale.US, n)

private fun date(ms: Long) = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))

@Composable
private fun Busy(text: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text)
    }
}

@Composable
private fun Message(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Preview(state: ImportState.Ready, viewModel: DataViewModel, modifier: Modifier) {
    val plan = state.plan
    if (plan.newWorkouts.isEmpty()) {
        Message(
            Icons.Filled.CheckCircle,
            "Nothing new to import",
            "All ${plural(plan.workouts.size, "workout")} in this file are already in Tonnage. Times read as ${plan.timeZoneId}.",
            "Close",
            viewModel::closeImport,
            modifier,
        )
        return
    }
    val custom = state.choices.values.count { it is ExerciseChoice.Custom }
    val matched = state.choices.size - custom
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(plural(plan.newWorkouts.size, "workout"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${plural(plan.setCount, "set")} · ${plural(state.choices.size, "exercise")}")
                Text("Times read as ${plan.timeZoneId}")
                val first = plan.firstStartedAt
                val last = plan.lastStartedAt
                if (first != null && last != null) Text("${date(first)} to ${date(last)}")
                if (plan.skipped > 0) {
                    Text(
                        "${plural(plan.skipped, "workout")} already imported will be skipped.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Exercises", style = MaterialTheme.typography.titleSmall)
                Text("$matched matched to the library" + if (custom > 0) ", $custom added as your own exercises" else "")
                TextButton(onClick = { viewModel.showReview(true) }) { Text("Check exercises") }
            }
        }
        Text(
            "Nothing is saved until you tap Import. Importing the same file again later skips workouts that are already here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = viewModel::runImport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Import ${plural(plan.newWorkouts.size, "workout")}")
        }
    }
}

@Composable
private fun Review(
    state: ImportState.Ready,
    library: Map<String, Exercise>,
    viewModel: DataViewModel,
    onSearch: (String) -> Unit,
    modifier: Modifier,
) {
    val toCheck = state.plan.matches.filter { it.confidence.needsReview }
    val automatic = state.plan.matches.filterNot { it.confidence.needsReview }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (toCheck.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Needs a look",
                        "Hevy called these something the library doesn't. Pick the matching exercise, or keep it as your own.",
                    )
                }
                items(toCheck, key = { "check:" + it.hevyName }) { match ->
                    MatchRow(match, state, library, viewModel, onSearch)
                }
            }
            if (automatic.isNotEmpty()) {
                item { SectionHeader("Matched automatically", "Tap Search to change any of these.") }
                items(automatic, key = { "auto:" + it.hevyName }) { match ->
                    MatchRow(match, state, library, viewModel, onSearch)
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = { viewModel.showReview(false) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun SectionHeader(title: String, body: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchRow(
    match: ExerciseMatch,
    state: ImportState.Ready,
    library: Map<String, Exercise>,
    viewModel: DataViewModel,
    onSearch: (String) -> Unit,
) {
    val choice = state.choices[match.hevyName]
    val chosenId = (choice as? ExerciseChoice.Existing)?.exerciseId
    val sets = state.plan.setCounts[match.hevyName] ?: 0
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Hevy: ${match.hevyName}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "→ " + (chosenId?.let { library[it]?.name ?: it } ?: "New exercise \"${match.hevyName}\"") + " · ${plural(sets, "set")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = (listOfNotNull(match.exerciseId) + match.suggestions).distinct().take(3)
            options.forEach { id ->
                val name = library[id]?.name ?: return@forEach
                FilterChip(
                    selected = chosenId == id,
                    onClick = { viewModel.choose(match.hevyName, ExerciseChoice.Existing(id)) },
                    label = { Text(name) },
                )
            }
            FilterChip(
                selected = choice is ExerciseChoice.Custom,
                onClick = { viewModel.choose(match.hevyName, ExerciseChoice.Custom) },
                label = { Text("Keep as my own") },
            )
            OutlinedButton(onClick = { onSearch(match.hevyName) }) { Text("Search…") }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
