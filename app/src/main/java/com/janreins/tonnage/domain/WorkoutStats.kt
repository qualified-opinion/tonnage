package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.PastSet
import com.janreins.tonnage.data.model.RecordSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet

/** An exercise in the active workout together with its library entry and sets. */
data class ExerciseBlock(
    val workoutExercise: WorkoutExercise,
    val exercise: Exercise?,
    val sets: List<WorkoutSet>,
    /** Routine targets, matched by set position; not another copy of logged data. */
    val plannedSets: List<WorkoutSet> = emptyList(),
) {
    val name: String get() = exercise?.name ?: "Unknown exercise"
    val trackingType: TrackingType get() = exercise?.trackingType ?: TrackingType.WEIGHT_REPS

    fun isPlanned(set: WorkoutSet): Boolean {
        val source = plannedSets.firstOrNull { it.position == set.position }
            ?: sets.filter { it.position < set.position }.maxByOrNull { it.position }
            ?: return false
        return set.weightKg == source.weightKg && set.reps == source.reps &&
            set.distanceKm == source.distanceKm && set.durationSec == source.durationSec
    }

    fun isEntered(set: WorkoutSet): Boolean = SetInput.isFilled(set, trackingType) && !isPlanned(set)
    val hasLoggedSets: Boolean get() = sets.any { SetInput.isFilled(it, trackingType) && (it.completed || isEntered(it)) }
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
    /** The set that set the record, when known (history); 0 on the finish screen. */
    val setId: Long = 0,
)

data class SessionSummary(
    val durationMs: Long,
    val exerciseCount: Int,
    val completedSets: Int,
    val incompleteSets: Int,
    val volumeKg: Double,
    /** Unticked entered values, excluding untouched routine targets and copied values. */
    val filledIncompleteSets: Int = 0,
    val plannedIncompleteSets: Int = 0,
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
            exerciseCount = blocks.count { block -> block.sets.any { it.completed && SetInput.isFilled(it, block.trackingType) } },
            completedSets = blocks.sumOf { block -> block.sets.count { it.completed && SetInput.isFilled(it, block.trackingType) } },
            incompleteSets = sets.count { !it.completed },
            volumeKg = sets.sumOf { setVolume(it) },
            filledIncompleteSets = blocks.sumOf { block ->
                block.sets.count { !it.completed && block.isEntered(it) }
            },
            plannedIncompleteSets = blocks.sumOf { block ->
                block.sets.count { !it.completed && SetInput.isFilled(it, block.trackingType) && block.isPlanned(it) }
            },
        )
    }

    /** The records tracked for each way of logging an exercise. */
    fun recordKinds(type: TrackingType): List<PrKind> = when (type) {
        TrackingType.WEIGHT_REPS -> listOf(PrKind.HEAVIEST_WEIGHT, PrKind.BEST_ONE_REP_MAX)
        TrackingType.BODYWEIGHT_REPS -> listOf(PrKind.MOST_REPS)
        else -> emptyList()
    }

    /** A set's value for one kind of record; 0 when it doesn't count. */
    fun recordValue(kind: PrKind, weightKg: Double?, reps: Int?): Double = when (kind) {
        PrKind.HEAVIEST_WEIGHT -> if ((reps ?: 0) > 0) weightKg ?: 0.0 else 0.0
        PrKind.BEST_ONE_REP_MAX -> estimatedOneRepMax(weightKg ?: 0.0, reps ?: 0)
        PrKind.MOST_REPS -> (reps ?: 0).toDouble()
    }

    /**
     * Every record in history, by workout id. Workouts are walked in date order: a workout sets a
     * record when its best set beats every earlier workout's. As on the finish screen, an
     * exercise's first workout sets none. Because this is worked out from the sets each time,
     * editing or deleting an old workout corrects the records after it.
     */
    fun recordsByWorkout(sets: List<RecordSet>): Map<Long, List<PersonalRecord>> {
        val best = mutableMapOf<Pair<String, PrKind>, Double>()
        val done = mutableSetOf<String>()
        val result = mutableMapOf<Long, MutableList<PersonalRecord>>()
        sets.sortedWith(compareBy({ it.startedAt }, { it.workoutId }))
            .groupBy { it.workoutId }
            .forEach { (workoutId, workoutSets) ->
                workoutSets.groupBy { it.exerciseId }.forEach { (exerciseId, exerciseSets) ->
                    val first = exerciseSets.first()
                    val type = TrackingType.entries.firstOrNull { it.name == first.trackingType } ?: TrackingType.WEIGHT_REPS
                    recordKinds(type).forEach { kind ->
                        val top = exerciseSets.maxBy { recordValue(kind, it.weightKg, it.reps) }
                        val value = recordValue(kind, top.weightKg, top.reps)
                        val before = best[exerciseId to kind] ?: 0.0
                        if (exerciseId in done && value > before) {
                            result.getOrPut(workoutId) { mutableListOf() } += PersonalRecord(
                                exerciseId = exerciseId,
                                exerciseName = first.exerciseName ?: "Unknown exercise",
                                kind = kind,
                                value = value,
                                previous = before,
                                setId = top.setId,
                            )
                        }
                        if (value > before) best[exerciseId to kind] = value
                    }
                    done += exerciseId
                }
            }
        return result
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

            recordKinds(block.trackingType).mapNotNull { kind ->
                record(
                    kind,
                    current.maxOf { recordValue(kind, it.weightKg, it.reps) },
                    past.maxOf { recordValue(kind, it.weightKg, it.reps) },
                )
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
