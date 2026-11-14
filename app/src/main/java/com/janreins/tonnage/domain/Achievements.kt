package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.BodyWeight
import com.janreins.tonnage.data.model.ProgressSet
import com.janreins.tonnage.data.model.SetType
import java.time.ZoneId

enum class AchievementKind { EQUIVALENT, RECORD, MILESTONE, WORKOUT_BEST, NEW_EXERCISE }

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

    /** Something heavy to compare a workout's total with, at a rough everyday weight. */
    data class Thing(val one: String, val many: String, val kg: Double)

    /** Lightest first. Weights are round, typical figures; the point is the picture, not the decimals. */
    val THINGS = listOf(
        Thing("a house cat", "house cats", 4.0),
        Thing("a golden retriever", "golden retrievers", 30.0),
        Thing("a washing machine", "washing machines", 70.0),
        Thing("a giant panda", "giant pandas", 100.0),
        Thing("a motorbike", "motorbikes", 200.0),
        Thing("a grand piano", "grand pianos", 450.0),
        Thing("a cow", "cows", 700.0),
        Thing("a small car", "small cars", 1_200.0),
        Thing("a hippo", "hippos", 1_500.0),
        Thing("a pickup truck", "pickup trucks", 2_500.0),
        Thing("an elephant", "elephants", 6_000.0),
        Thing("a T. rex", "T. rexes", 8_000.0),
        Thing("a school bus", "school buses", 12_000.0),
        Thing("a fire truck", "fire trucks", 20_000.0),
        Thing("a loaded semi-truck", "loaded semi-trucks", 36_000.0),
        Thing("a jumbo jet", "jumbo jets", 180_000.0),
    )

    /**
     * The workout's total as the heaviest thing it reaches, e.g. "You lifted about 2 school buses";
     * null under the lightest thing. Up to 1.5 of a thing reads as one.
     */
    fun equivalent(totalKg: Double): Achievement? {
        val thing = THINGS.lastOrNull { totalKg >= it.kg } ?: return null
        val count = Math.round(totalKg / thing.kg).toInt()
        val what = if (totalKg / thing.kg < 1.5) thing.one else "$count ${thing.many}"
        return Achievement(
            AchievementKind.EQUIVALENT,
            "You lifted about $what",
            "${Format.volume(totalKg)} in total; ${thing.one} weighs about ${Format.volume(thing.kg)}",
        )
    }

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
     * What the total weighs as, then records, then milestones this workout reaches, then bests for a whole workout and
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

        equivalent(workoutSets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) })?.let { result += it }

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
