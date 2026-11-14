package com.janreins.tonnage.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.ExerciseOptions
import com.janreins.tonnage.domain.Achievement
import com.janreins.tonnage.domain.AchievementKind
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.MonthStats
import com.janreins.tonnage.domain.MuscleSets
import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.ui.components.BodyMap
import com.janreins.tonnage.ui.components.MonthCalendar
import com.janreins.tonnage.ui.components.achievementIcon
import com.janreins.tonnage.ui.components.monthName
import com.janreins.tonnage.ui.theme.PrGold
import java.time.LocalDate

/** Ease that overshoots a little and settles, so each badge pops rather than slides. */
private val PopEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private const val POP_START_MS = 250
private const val POP_GAP_MS = 160

/**
 * The workout's achievements as badges that pop in one after another: what the total weighs as
 * and milestones in apricot, records in gold, workout bests and new exercises in green.
 */
@Composable
internal fun AchievementsSection(achievements: List<Achievement>) {
    if (achievements.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val count = achievements.size
        Text(
            if (count == 1) "1 achievement" else "$count achievements",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        achievements.forEachIndexed { index, achievement -> PoppingBadge(achievement, index) }
    }
}

@Composable
private fun PoppingBadge(achievement: Achievement, index: Int) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val delay = POP_START_MS + index.coerceAtMost(12) * POP_GAP_MS
    AnimatedVisibility(
        visibleState = shown,
        enter = fadeIn(tween(200, delayMillis = delay)) + scaleIn(tween(420, delayMillis = delay, easing = PopEasing), initialScale = 0.6f),
    ) {
        AchievementBadge(achievement)
    }
}

@Composable
private fun accent(achievement: Achievement): Color = when (achievement.kind) {
    AchievementKind.RECORD -> PrGold
    AchievementKind.EQUIVALENT, AchievementKind.MILESTONE -> MaterialTheme.colorScheme.tertiary
    AchievementKind.WORKOUT_BEST, AchievementKind.NEW_EXERCISE -> MaterialTheme.colorScheme.primary
}

@Composable
private fun AchievementBadge(achievement: Achievement) {
    val color = accent(achievement)
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), shape)
            .border(1.dp, color.copy(alpha = 0.4f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(color.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(achievementIcon(achievement), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val label = when (achievement.kind) {
                AchievementKind.EQUIVALENT -> "Total lifted"
                AchievementKind.RECORD -> "New record"
                AchievementKind.MILESTONE -> "Milestone"
                AchievementKind.WORKOUT_BEST -> "Workout best"
                AchievementKind.NEW_EXERCISE -> "New exercise"
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(achievement.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (achievement.detail.isNotBlank()) {
                Text(achievement.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The muscles this workout worked, on the same body map as during the workout. */
@Composable
internal fun FinishBodyMap(muscles: List<MuscleSets>) {
    val setsByMuscle = remember(muscles) { muscles.associate { it.muscle to it.sets } }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPart by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BodyMap(
            setsByMuscle = setsByMuscle,
            selected = selected,
            onSelect = { region ->
                val same = region?.muscle == selected && region?.part == selectedPart
                selected = if (same) null else region?.muscle
                selectedPart = if (same) null else region?.part
            },
            description = "Body map of sets per muscle this workout: " +
                muscles.joinToString { "${muscleName(it.muscle)} ${it.sets} ${if (it.sets == 1) "set" else "sets"}" },
            fullAt = LIVE_MAP_FULL_AT,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        selected?.let { muscle ->
            val name = muscleName(muscle)
            val part = selectedPart?.takeUnless { it.equals(name, ignoreCase = true) }
            val sets = setsByMuscle[muscle] ?: 0
            Text(
                "${part?.let { "$it · " }.orEmpty()}$name: $sets ${if (sets == 1) "set" else "sets"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun muscleName(muscle: String): String =
    if (muscle == Progress.OTHER_MUSCLE) "Other" else ExerciseOptions.display(muscle)

/** This month so far, counting the workout being finished, with its days on a calendar. */
@Composable
internal fun FinishMonthSection(month: MonthStats, day: LocalDate?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(monthName(month.month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        val workouts = if (month.workouts == 1) "Your first workout this month" else "${month.workouts} workouts this month, counting this one"
        Text(
            "$workouts · ${Format.count(month.sets)} working sets · ${Format.volume(month.volumeKg)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MonthCalendar(month.month, month.workoutDays, today = day)
    }
}
