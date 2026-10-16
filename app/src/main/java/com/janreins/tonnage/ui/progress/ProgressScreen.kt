package com.janreins.tonnage.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.ExerciseOptions
import com.janreins.tonnage.domain.ExerciseProgress
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.Milestone
import com.janreins.tonnage.domain.MuscleSets
import com.janreins.tonnage.domain.NextMilestone
import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.domain.ProgressMetric
import com.janreins.tonnage.ui.components.BarChart
import com.janreins.tonnage.ui.components.BodyMap
import com.janreins.tonnage.ui.components.ComingSoon
import com.janreins.tonnage.ui.components.bodyMapColor
import com.janreins.tonnage.ui.components.rememberBodyMapArt
import com.janreins.tonnage.ui.theme.PrGold
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    overview: ProgressOverview?,
    viewModel: ProgressViewModel,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Progress") }) },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when {
            overview == null -> Box(content, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            overview.workoutCount == 0 -> ComingSoon(
                Icons.AutoMirrored.Filled.ShowChart,
                "Nothing to show yet",
                "Finish a workout, or import your Hevy history from the More tab, to see charts, weekly stats and milestones.",
                content,
            )
            else -> LazyColumn(
                content,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { WeekCard(overview, viewModel) }
                item { MusclesCard(overview.muscles, isThisWeek = overview.selectedWeek == overview.weeks.lastIndex) }
                item { MilestonesCard(overview) }
                item { SectionTitle("Exercises") }
                items(overview.exercises, key = { it.exerciseId }) { exercise ->
                    ExerciseRow(exercise, onClick = { onOpenExercise(exercise.exerciseId) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

private fun weekName(week: LocalDate, isThisWeek: Boolean, isLastWeek: Boolean): String = when {
    isThisWeek -> "This week"
    isLastWeek -> "Last week"
    else -> "Week of ${week.format(dayMonth)}"
}

@Composable
private fun WeekCard(overview: ProgressOverview, viewModel: ProgressViewModel) {
    val week = overview.week
    val last = overview.weeks.lastIndex
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousWeek, enabled = overview.selectedWeek > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        weekName(week.weekStart, overview.selectedWeek == last, overview.selectedWeek == last - 1),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${week.weekStart.format(dayMonth)} – ${week.weekStart.plusDays(6).format(dayMonth)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::nextWeek, enabled = overview.selectedWeek < last) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Stat(week.workouts.toString(), if (week.workouts == 1) "workout" else "workouts")
                Stat(week.sets.toString(), "working sets")
                Stat(Format.volume(week.volumeKg), "volume")
            }
            HorizontalDivider()
            Text("Workouts per week", style = MaterialTheme.typography.labelLarge)
            BarChart(
                values = overview.weeks.map { it.workouts },
                labels = overview.weeks.map { it.weekStart.format(dayMonth) },
                selected = overview.selectedWeek,
                onSelect = viewModel::selectWeek,
                description = "Workouts per week for the last ${overview.weeks.size} weeks: " +
                    overview.weeks.joinToString { "${it.weekStart.format(dayMonth)} ${it.workouts}" },
            )
            StreakLine(overview)
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StreakLine(overview: ProgressOverview) {
    val streak = overview.streak
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = if (streak.current > 0) PrGold else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        val current = when (streak.current) {
            0 -> "No streak right now"
            1 -> "1 week in a row"
            else -> "${streak.current} weeks in a row"
        }
        Text("$current · best ${streak.longest} ${if (streak.longest == 1) "week" else "weeks"}", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun muscleName(muscle: String): String =
    if (muscle == Progress.OTHER_MUSCLE) "Other" else ExerciseOptions.display(muscle)

private fun setCount(sets: Int): String = "$sets ${if (sets == 1) "set" else "sets"}"

/**
 * Working sets per main muscle as a heat map on front and back body figures: the more sets, the
 * deeper the green. Tapping a muscle shows its sets, and the full list sits behind "Show list".
 */
@Composable
private fun MusclesCard(muscles: List<MuscleSets>, isThisWeek: Boolean) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPart by rememberSaveable { mutableStateOf<String?>(null) }
    var showList by rememberSaveable { mutableStateOf(false) }
    val setsByMuscle = muscles.associate { it.muscle to it.sets }
    val mapped = rememberBodyMapArt().muscles
    val unmapped = muscles.filter { it.muscle !in mapped }.sumOf { it.sets }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Muscles worked", style = MaterialTheme.typography.titleMedium)
            Text(
                "Working sets by main muscle, for the week above. Tap a muscle to see its sets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (muscles.isEmpty()) {
                Text(if (isThisWeek) "No sets logged yet this week." else "No sets logged that week.", style = MaterialTheme.typography.bodyMedium)
            }
            BodyMap(
                setsByMuscle = setsByMuscle,
                selected = selected,
                onSelect = { region ->
                    val same = region?.muscle == selected && region?.part == selectedPart
                    selected = if (same) null else region?.muscle
                    selectedPart = if (same) null else region?.part
                },
                description = if (muscles.isEmpty()) {
                    "Body map with no muscles worked"
                } else {
                    "Body map of sets per muscle: " + muscles.joinToString { "${muscleName(it.muscle)} ${setCount(it.sets)}" }
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                selected?.let { muscle ->
                    val name = muscleName(muscle)
                    val part = selectedPart?.takeUnless { it.equals(name, ignoreCase = true) }
                    "${part?.let { "$it · " }.orEmpty()}$name: ${setCount(setsByMuscle[muscle] ?: 0)}"
                } ?: "Tap a muscle to see its sets",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            HeatLegend(Modifier.align(Alignment.CenterHorizontally))
            if (unmapped > 0) {
                Text(
                    "Plus ${setCount(unmapped)} of exercises with no muscle set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (muscles.isNotEmpty()) {
                TextButton(onClick = { showList = !showList }) { Text(if (showList) "Hide list" else "Show list") }
            }
            if (showList) MuscleBars(muscles)
        }
    }
}

/** Swatches from unworked to most worked, so the shading can be read. */
@Composable
private fun HeatLegend(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("None", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { shade ->
            Box(Modifier.size(width = 22.dp, height = 10.dp).background(bodyMapColor(shade), RoundedCornerShape(3.dp)))
        }
        Text("Most sets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Working sets per main muscle, as bars whose colour deepens with the number of sets. */
@Composable
private fun MuscleBars(muscles: List<MuscleSets>) {
    val most = muscles.maxOfOrNull { it.sets } ?: 1
    muscles.forEach { muscle ->
        val share = muscle.sets / most.toFloat()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                muscleName(muscle.muscle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(110.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.weight(1f).height(14.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(share)
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f + 0.7f * share),
                            RoundedCornerShape(4.dp),
                        ),
                )
            }
            Text(
                muscle.sets.toString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(32.dp).padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun MilestonesCard(overview: ProgressOverview) {
    val report = overview.milestones
    var showAll by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Milestones", style = MaterialTheme.typography.titleMedium)
            if (report.next.isNotEmpty()) {
                Text("Next up", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                report.next.forEach { NextRow(it) }
            }
            if (report.achieved.isNotEmpty()) {
                Text(
                    "Reached (${report.achieved.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val shown = if (showAll) report.achieved else report.achieved.take(5)
                shown.forEach { AchievedRow(it) }
                if (report.achieved.size > 5) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Show fewer" else "Show all ${report.achieved.size}")
                    }
                }
            }
        }
    }
}

@Composable
private fun NextRow(next: NextMilestone) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(next.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${next.current} / ${next.target}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(progress = { next.fraction.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AchievedRow(milestone: Milestone) {
    val date = java.time.Instant.ofEpochMilli(milestone.achievedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = PrGold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(milestone.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseProgress, onClick: () -> Unit) {
    val metric = ProgressMetric.forType(exercise.trackingType).first()
    val last = java.time.Instant.ofEpochMilli(exercise.lastStartedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${exercise.sessions} ${if (exercise.sessions == 1) "workout" else "workouts"} · last ${last.format(dayMonth)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(metric.format(exercise.best), style = MaterialTheme.typography.titleSmall)
            Text("best ${metric.label.replaceFirstChar { it.lowercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
