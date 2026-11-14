package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.BodyWeight
import com.janreins.tonnage.data.model.ProgressSet
import com.janreins.tonnage.data.model.SetType
import java.time.ZoneId

enum class AchievementKind { RECORD, MILESTONE, WORKOUT_BEST, NEW_EXERCISE }

/** One reward shown on the finish screen, such as a record or a milestone reached. */
data class Achievement(
    val kind: AchievementKind,
    val title: String,
    val detail: String = "",
    /** Which milestone, for [AchievementKind.MILESTONE]. */
    val milestoneKind: MilestoneKind? = null,
)

/**
 * The achievements of the workout being finished, worked out from finished history the same way
 * Progress and History do, so what pops up now is what those screens show after saving.
 */
object Achievements {

    /** The working sets of [blocks] that will be saved, as Progress counts them. */
    fun progressSets(workoutId: Long, startedAt: Long, blocks: List<ExerciseBlock>): List<ProgressSet> =
        blocks.flatMap { block ->
            block.sets
                .filter { it.completed && it.type != SetType.WARMUP && SetInput.isFilled(it, block.trackingType) }
                .map { set ->
                    ProgressSet(
                        workoutId = workoutId,
                        startedAt = startedAt,
                        exerciseId = block.workoutExercise.exerciseId,
                        exerciseName = block.exercise?.name,
                        trackingType = block.trackingType.name,
                        primaryMuscles = block.exercise?.primaryMuscles,
                        weightKg = set.weightKg,
                        reps = set.reps,
                        distanceKm = set.distanceKm,
                        durationSec = set.durationSec,
                    )
                }
        }

    /**
     * Records first, then milestones this workout reaches, then bests for a whole workout and
     * exercises done for the first time. Workout bests and new exercises need some earlier history,
     * so a first workout (or the first one before a Hevy import) isn't all achievements.
     */
    fun forWorkout(
        workout: WorkoutStart,
        workoutSets: List<ProgressSet>,
        history: List<WorkoutStart>,
        historySets: List<ProgressSet>,
        records: List<PersonalRecord>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        bodyWeights: List<BodyWeight> = emptyList(),
    ): List<Achievement> {
        val result = mutableListOf<Achievement>()

        records.forEach { record ->
            result += Achievement(
                AchievementKind.RECORD,
                record.exerciseName,
                "${record.kind.label} ${WorkoutStats.recordText(record.kind, record.value)} " +
                    "(was ${WorkoutStats.recordText(record.kind, record.previous)})",
            )
        }

        val before = Milestones.report(history, historySets, now, zone, bodyWeights).achieved.map { it.key }.toSet()
        Milestones.report(history + workout, historySets + workoutSets, now, zone, bodyWeights).achieved
            .filter { it.workoutId == workout.workoutId && it.key !in before }
            .sortedBy { it.kind.ordinal }
            .forEach { result += Achievement(AchievementKind.MILESTONE, it.title, milestoneKind = it.kind) }

        if (history.isNotEmpty()) {
            val past = historySets.groupBy { it.workoutId }.values
            val volume = workoutSets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
            val bestVolume = past.maxOfOrNull { sets -> sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) } } ?: 0.0
            if (bestVolume > 0.0 && volume > bestVolume) {
                result += Achievement(AchievementKind.WORKOUT_BEST, "Biggest workout yet", "${Format.volume(volume)} lifted (was ${Format.volume(bestVolume)})")
            }
            val mostSets = past.maxOfOrNull { it.size } ?: 0
            if (mostSets > 0 && workoutSets.size > mostSets) {
                result += Achievement(AchievementKind.WORKOUT_BEST, "Most sets in one workout", "${workoutSets.size} working sets (was $mostSets)")
            }
            val done = historySets.map { it.exerciseId }.toSet()
            workoutSets.filter { it.exerciseId !in done }.distinctBy { it.exerciseId }.forEach { set ->
                result += Achievement(AchievementKind.NEW_EXERCISE, set.exerciseName ?: "Unknown exercise", "First time")
            }
        }
        return result
    }
}
