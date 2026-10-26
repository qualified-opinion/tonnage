package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet

/** What to aim for today, shown under the exercise name; tapping it fills [weightKg] and [reps]. */
data class ProgressionHint(val text: String, val weightKg: Double?, val reps: Int)

/**
 * Double progression from last time's sets. With weights: once every working set at the top
 * weight reached the target reps, add [STEP_KG]; otherwise repeat the weight until they all do.
 * Bodyweight exercises aim for one more rep than the best set. Warm-ups never count.
 */
object Progression {
    const val STEP_KG = 2.5

    /**
     * [previous] is last time's completed sets for one exercise and [planned] this block's
     * routine targets (may be empty). Null when there is nothing sensible to suggest.
     */
    fun hint(previous: List<WorkoutSet>, type: TrackingType, planned: List<WorkoutSet>): ProgressionHint? = when (type) {
        TrackingType.WEIGHT_REPS -> weightHint(previous, planned)
        TrackingType.BODYWEIGHT_REPS -> bodyweightHint(previous)
        TrackingType.DURATION, TrackingType.DISTANCE_DURATION -> null
    }

    private fun weightHint(previous: List<WorkoutSet>, planned: List<WorkoutSet>): ProgressionHint? {
        val working = previous.filter { it.type != SetType.WARMUP && (it.weightKg ?: 0.0) > 0.0 && (it.reps ?: 0) > 0 }
        if (working.isEmpty()) return null
        val target = planned.filter { it.type != SetType.WARMUP }.mapNotNull { it.reps?.takeIf { r -> r > 0 } }.maxOrNull()
            ?: working.first().reps!!
        val top = working.maxOf { it.weightKg!! }
        val topReps = working.filter { it.weightKg == top }.map { it.reps!! }
        val last = "Last: ${Format.number(top)} × ${topReps.joinToString(", ")}"
        return if (topReps.all { it >= target }) {
            val next = top + STEP_KG
            ProgressionHint("$last · try ${Format.number(next)} × $target", next, target)
        } else {
            ProgressionHint("$last · try ${Format.number(top)} × $target on all sets", top, target)
        }
    }

    private fun bodyweightHint(previous: List<WorkoutSet>): ProgressionHint? {
        val reps = previous.filter { it.type != SetType.WARMUP }.mapNotNull { it.reps?.takeIf { r -> r > 0 } }
        if (reps.isEmpty()) return null
        val next = reps.max() + 1
        return ProgressionHint("Last: ${reps.joinToString(", ")} · try $next", null, next)
    }

    /** The sets tapping the hint fills: unticked working sets that are blank or an untouched plan. */
    fun fillTargets(block: ExerciseBlock): List<WorkoutSet> =
        block.sets.filter { !it.completed && it.type != SetType.WARMUP && !block.isEntered(it) }

    /** [set] with the hint's numbers; a bodyweight hint leaves any added weight as it was. */
    fun applied(set: WorkoutSet, hint: ProgressionHint): WorkoutSet =
        set.copy(weightKg = hint.weightKg ?: set.weightKg, reps = hint.reps)
}
