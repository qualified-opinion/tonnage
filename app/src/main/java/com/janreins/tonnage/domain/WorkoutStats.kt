package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.PastSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet

/** An exercise in the active workout together with its library entry and sets. */
data class ExerciseBlock(
    val workoutExercise: WorkoutExercise,
    val exercise: Exercise?,
    val sets: List<WorkoutSet>,
) {
    val name: String get() = exercise?.name ?: "Unknown exercise"
    val trackingType: TrackingType get() = exercise?.trackingType ?: TrackingType.WEIGHT_REPS
}

enum class PrKind(val label: String) {
    HEAVIEST_WEIGHT("Heaviest weight"),
    BEST_ONE_REP_MAX("Best estimated 1RM"),
    MOST_REPS("Most reps"),
}

data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val kind: PrKind,
    val value: Double,
    val previous: Double,
)

data class SessionSummary(
    val durationMs: Long,
    val exerciseCount: Int,
    val completedSets: Int,
    val incompleteSets: Int,
    val volumeKg: Double,
)

object WorkoutStats {

    /** Epley estimate: weight × (1 + reps / 30). A single rep is the weight itself. */
    fun estimatedOneRepMax(weightKg: Double, reps: Int): Double = when {
        reps <= 0 || weightKg <= 0.0 -> 0.0
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }

    /** Weight × reps of a completed working set; warm-ups don't count towards volume. */
    fun setVolume(set: WorkoutSet): Double {
        if (!set.completed || set.type == SetType.WARMUP) return 0.0
        val weight = set.weightKg ?: return 0.0
        val reps = set.reps ?: return 0.0
        return weight * reps
    }

    fun summarize(blocks: List<ExerciseBlock>, startedAt: Long, now: Long): SessionSummary {
        val sets = blocks.flatMap { it.sets }
        return SessionSummary(
            durationMs = (now - startedAt).coerceAtLeast(0),
            exerciseCount = blocks.count { block -> block.sets.any { it.completed } },
            completedSets = sets.count { it.completed },
            incompleteSets = sets.count { !it.completed },
            volumeKg = sets.sumOf { setVolume(it) },
        )
    }

    /**
     * Records beaten in this workout compared with earlier finished workouts. An exercise with no
     * history yields no records, so a first session (or one before the Hevy import) isn't all PRs.
     */
    fun detectRecords(blocks: List<ExerciseBlock>, history: List<PastSet>): List<PersonalRecord> {
        val historyByExercise = history.groupBy { it.exerciseId }
        return blocks.flatMap { block ->
            val exerciseId = block.workoutExercise.exerciseId
            val past = historyByExercise[exerciseId].orEmpty()
            if (past.isEmpty()) return@flatMap emptyList()
            val current = block.sets.filter { it.completed && it.type != SetType.WARMUP }
            if (current.isEmpty()) return@flatMap emptyList()

            fun record(kind: PrKind, now: Double, before: Double): PersonalRecord? =
                if (now > before && now > 0.0) PersonalRecord(exerciseId, block.name, kind, now, before) else null

            when (block.trackingType) {
                TrackingType.WEIGHT_REPS -> listOfNotNull(
                    record(
                        PrKind.HEAVIEST_WEIGHT,
                        current.maxOf { if ((it.reps ?: 0) > 0) it.weightKg ?: 0.0 else 0.0 },
                        past.maxOf { if ((it.reps ?: 0) > 0) it.weightKg ?: 0.0 else 0.0 },
                    ),
                    record(
                        PrKind.BEST_ONE_REP_MAX,
                        current.maxOf { estimatedOneRepMax(it.weightKg ?: 0.0, it.reps ?: 0) },
                        past.maxOf { estimatedOneRepMax(it.weightKg ?: 0.0, it.reps ?: 0) },
                    ),
                )
                TrackingType.BODYWEIGHT_REPS -> listOfNotNull(
                    record(
                        PrKind.MOST_REPS,
                        current.maxOf { (it.reps ?: 0).toDouble() },
                        past.maxOf { (it.reps ?: 0).toDouble() },
                    ),
                )
                else -> emptyList()
            }
        }
    }

    /** Label shown in the set column: W, D, F, or the number among normal sets. */
    fun setLabels(sets: List<WorkoutSet>): List<String> {
        var normal = 0
        return sets.map { set ->
            if (set.type == SetType.NORMAL) {
                normal += 1
                normal.toString()
            } else {
                set.type.shortLabel
            }
        }
    }

    /** Describes the next unfinished set after [completedSetId], for the rest timer. */
    fun nextSetLabel(blocks: List<ExerciseBlock>, completedSetId: Long): String? {
        val ordered = blocks.flatMap { block ->
            val labels = setLabels(block.sets)
            block.sets.mapIndexed { index, set -> Triple(block, set, labels[index]) }
        }
        val start = ordered.indexOfFirst { it.second.id == completedSetId }
        if (start < 0) return null
        val next = ordered.drop(start + 1).firstOrNull { !it.second.completed } ?: return null
        val (block, set, label) = next
        val setName = if (set.type == SetType.NORMAL) "Set $label" else set.type.label
        val target = Format.setTarget(set, block.trackingType)
        return listOf(block.name, setName, target).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
