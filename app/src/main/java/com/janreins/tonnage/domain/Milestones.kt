package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.ProgressSet
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class MilestoneKind { WORKOUTS, VOLUME, STREAK, LIFT }

/** A milestone reached, e.g. "100 workouts", with the workout that reached it. */
data class Milestone(
    val key: String,
    val kind: MilestoneKind,
    val title: String,
    val achievedAt: Long,
    val workoutId: Long,
)

/** The next milestone of one kind, with how far along it is. */
data class NextMilestone(
    val key: String,
    val kind: MilestoneKind,
    val title: String,
    val current: String,
    val target: String,
    /** 0 to 1. */
    val fraction: Double,
)

data class MilestoneReport(
    /** Newest first. */
    val achieved: List<Milestone>,
    val next: List<NextMilestone>,
)

/** A workout's start time and id, for milestones that count workouts. */
data class WorkoutStart(val workoutId: Long, val startedAt: Long)

/**
 * Milestones are worked out from history each time, like personal records, so they follow
 * edits, deletes and imports, and an imported workout reaches them on the date it was done.
 */
object Milestones {

    val WORKOUT_COUNTS = listOf(1, 5, 10, 25, 50, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000)
    val VOLUME_KG = listOf(10_000.0, 25_000.0, 50_000.0, 100_000.0, 250_000.0, 500_000.0, 1_000_000.0, 2_500_000.0, 5_000_000.0, 10_000_000.0)
    val STREAK_WEEKS = listOf(2, 4, 8, 12, 16, 26, 52, 104)
    val LIFT_KG = (40..300 step 20).map { it.toDouble() }

    /** Library ids of the lifts with weight milestones, and how they're named. */
    val KEY_LIFTS = linkedMapOf(
        "Barbell_Bench_Press_-_Medium_Grip" to "Bench press",
        "Barbell_Squat" to "Squat",
        "Barbell_Deadlift" to "Deadlift",
        "Standing_Military_Press" to "Overhead press",
    )

    private fun count(n: Int) = if (n == 1) "First workout" else "${Format.count(n)} workouts"
    private fun volume(kg: Double) = "${Format.volume(kg)} lifted"
    private fun streak(weeks: Int) = "$weeks-week streak"
    private fun lift(name: String, kg: Double) = "$name ${Format.number(kg)} kg"

    fun report(
        workouts: List<WorkoutStart>,
        sets: List<ProgressSet>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MilestoneReport {
        val ordered = workouts.sortedWith(compareBy({ it.startedAt }, { it.workoutId }))
        val achieved = mutableListOf<Milestone>()
        val next = mutableListOf<NextMilestone>()

        // Workouts done.
        WORKOUT_COUNTS.forEach { n ->
            ordered.getOrNull(n - 1)?.let { achieved += Milestone("workouts-$n", MilestoneKind.WORKOUTS, count(n), it.startedAt, it.workoutId) }
        }
        WORKOUT_COUNTS.firstOrNull { it > ordered.size }?.let { n ->
            next += NextMilestone("workouts-$n", MilestoneKind.WORKOUTS, count(n), Format.count(ordered.size), Format.count(n), ordered.size / n.toDouble())
        }

        // Total weight lifted, warm-ups excluded.
        val volumeByWorkout = sets.groupBy { it.workoutId }.mapValues { (_, list) -> list.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) } }
        var total = 0.0
        var volumeIndex = 0
        ordered.forEach { workout ->
            total += volumeByWorkout[workout.workoutId] ?: 0.0
            while (volumeIndex < VOLUME_KG.size && total >= VOLUME_KG[volumeIndex]) {
                val kg = VOLUME_KG[volumeIndex]
                achieved += Milestone("volume-${kg.toLong()}", MilestoneKind.VOLUME, volume(kg), workout.startedAt, workout.workoutId)
                volumeIndex += 1
            }
        }
        VOLUME_KG.getOrNull(volumeIndex)?.let { kg ->
            next += NextMilestone("volume-${kg.toLong()}", MilestoneKind.VOLUME, volume(kg), Format.volume(total), Format.volume(kg), total / kg)
        }

        // Weeks in a row; reached by the first workout of the week that makes the run long enough.
        var run = 0
        var longest = 0
        var previousWeek: LocalDate? = null
        ordered.groupBy { Progress.weekStart(it.startedAt, zone) }.toSortedMap().forEach { (week, weekWorkouts) ->
            val before = previousWeek
            run = if (before != null && ChronoUnit.WEEKS.between(before, week) == 1L) run + 1 else 1
            if (run > longest) {
                STREAK_WEEKS.filter { it in (longest + 1)..run }.forEach { weeks ->
                    val first = weekWorkouts.first()
                    achieved += Milestone("streak-$weeks", MilestoneKind.STREAK, streak(weeks), first.startedAt, first.workoutId)
                }
                longest = run
            }
            previousWeek = week
        }
        STREAK_WEEKS.firstOrNull { it > longest }?.let { weeks ->
            val current = Progress.streak(ordered.map { it.startedAt }, now, zone).current
            next += NextMilestone("streak-$weeks", MilestoneKind.STREAK, streak(weeks), "$current", "$weeks weeks", current / weeks.toDouble())
        }

        // Heaviest weight on the key lifts.
        KEY_LIFTS.forEach { (exerciseId, name) ->
            val liftSets = sets.filter { it.exerciseId == exerciseId && (it.reps ?: 0) > 0 && (it.weightKg ?: 0.0) > 0.0 }
            if (liftSets.isEmpty()) return@forEach
            var best = 0.0
            var liftIndex = 0
            liftSets.sortedWith(compareBy({ it.startedAt }, { it.workoutId })).forEach { set ->
                best = maxOf(best, set.weightKg ?: 0.0)
                while (liftIndex < LIFT_KG.size && best >= LIFT_KG[liftIndex]) {
                    val kg = LIFT_KG[liftIndex]
                    achieved += Milestone("lift-$exerciseId-${kg.toInt()}", MilestoneKind.LIFT, lift(name, kg), set.startedAt, set.workoutId)
                    liftIndex += 1
                }
            }
            LIFT_KG.getOrNull(liftIndex)?.let { kg ->
                next += NextMilestone("lift-$exerciseId-${kg.toInt()}", MilestoneKind.LIFT, lift(name, kg), "${Format.number(best)} kg", "${Format.number(kg)} kg", best / kg)
            }
        }

        return MilestoneReport(
            achieved = achieved.sortedWith(compareByDescending<Milestone> { it.achievedAt }.thenByDescending { it.kind.ordinal }),
            next = next.sortedByDescending { it.fraction },
        )
    }
}
