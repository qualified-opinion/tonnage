package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.BodyWeight
import com.janreins.tonnage.data.model.ProgressSet
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.floor

enum class MilestoneKind { WORKOUTS, VOLUME, STREAK, LIFT, STRENGTH, REPS, VARIETY, TIME, MONTH }

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
    /** Closest first. */
    val next: List<NextMilestone>,
)

/** A workout's start time and id, for milestones that count workouts; [endedAt] counts time trained. */
data class WorkoutStart(val workoutId: Long, val startedAt: Long, val endedAt: Long? = null)

/**
 * Milestones are worked out from history each time, like personal records, so they follow
 * edits, deletes and imports, and an imported workout reaches them on the date it was done.
 */
object Milestones {

    val WORKOUT_COUNTS = listOf(1, 5, 10, 25, 50, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000)
    val VOLUME_KG = listOf(10_000.0, 25_000.0, 50_000.0, 100_000.0, 250_000.0, 500_000.0, 1_000_000.0, 2_500_000.0, 5_000_000.0, 10_000_000.0)
    val STREAK_WEEKS = listOf(2, 4, 8, 12, 16, 26, 52, 104)
    val LIFT_KG = (40..300 step 20).map { it.toDouble() }

    /** Key lifts as a multiple of the body weight logged on or before the day. */
    val STRENGTH_RATIOS = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 3.0)

    /** Reps in one set of the key body weight exercises. */
    val SET_REPS = listOf(5, 10, 15, 20, 25, 30, 40, 50)

    /** Different exercises ever done. */
    val EXERCISE_COUNTS = listOf(10, 25, 50, 75, 100, 150, 200)

    /** Hours trained, from start to finish of each workout. */
    val HOURS = listOf(10, 25, 50, 100, 250, 500, 1000)

    /** Workouts within one calendar month. */
    val MONTH_COUNTS = listOf(4, 8, 12, 16, 20, 25)

    /** A workout counts for at most this long, so one left running overnight doesn't add 12 hours. */
    const val MAX_COUNTED_MS = 4 * 60 * 60 * 1000L

    /** Library ids of the lifts with weight milestones, and how they're named. */
    val KEY_LIFTS = linkedMapOf(
        "Barbell_Bench_Press_-_Medium_Grip" to "Bench press",
        "Barbell_Squat" to "Squat",
        "Barbell_Deadlift" to "Deadlift",
        "Standing_Military_Press" to "Overhead press",
    )

    /** Body weight exercises with reps-in-one-set milestones; dips count whichever version was logged. */
    val KEY_REP_EXERCISES = linkedMapOf(
        "pull-ups" to setOf("Pullups"),
        "chin-ups" to setOf("Chin-Up"),
        "push-ups" to setOf("Pushups"),
        "dips" to setOf("Dips_-_Triceps_Version", "Parallel_Bar_Dip", "Dips_-_Chest_Version"),
    )

    private fun count(n: Int) = if (n == 1) "First workout" else "${Format.count(n)} workouts"
    private fun volume(kg: Double) = "${Format.volume(kg)} lifted"
    private fun streak(weeks: Int) = "$weeks-week streak"
    private fun lift(name: String, kg: Double) = "$name ${Format.number(kg)} kg"
    fun ratio(value: Double) = "${Format.number(value)}×"
    private fun strength(name: String, ratio: Double) = "$name ${ratio(ratio)} body weight"
    private fun reps(name: String, n: Int) = "$n $name in one set"
    private fun exercises(n: Int) = "${Format.count(n)} different exercises"
    private fun hours(n: Int) = "${Format.count(n)} hours trained"
    private fun month(n: Int) = "$n workouts in a month"

    fun report(
        workouts: List<WorkoutStart>,
        sets: List<ProgressSet>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        bodyWeights: List<BodyWeight> = emptyList(),
    ): MilestoneReport {
        val ordered = workouts.sortedWith(compareBy({ it.startedAt }, { it.workoutId }))
        val orderedSets = sets.sortedWith(compareBy({ it.startedAt }, { it.workoutId }))
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
        runningTotal(
            ordered, { volumeByWorkout[it.workoutId] ?: 0.0 }, VOLUME_KG, achieved,
        ) { kg -> "volume-${kg.toLong()}" to volume(kg) }
            .let { (total, kg) ->
                kg?.let { next += NextMilestone("volume-${it.toLong()}", MilestoneKind.VOLUME, volume(it), Format.volume(total), Format.volume(it), total / it) }
            }

        // Time trained.
        val hourSteps = HOURS.map { it.toDouble() }
        runningTotal(ordered, { durationHours(it) }, hourSteps, achieved, MilestoneKind.TIME) { h -> "hours-${h.toInt()}" to hours(h.toInt()) }
            .let { (total, h) ->
                h?.let { next += NextMilestone("hours-${it.toInt()}", MilestoneKind.TIME, hours(it.toInt()), "${floor(total).toInt()} h", "${it.toInt()} h", total / it) }
            }

        // Different exercises tried.
        val exercisesByWorkout = sets.groupBy { it.workoutId }.mapValues { (_, list) -> list.map { it.exerciseId }.toSet() }
        val seen = mutableSetOf<String>()
        var exerciseIndex = 0
        ordered.forEach { workout ->
            seen += exercisesByWorkout[workout.workoutId].orEmpty()
            while (exerciseIndex < EXERCISE_COUNTS.size && seen.size >= EXERCISE_COUNTS[exerciseIndex]) {
                val n = EXERCISE_COUNTS[exerciseIndex]
                achieved += Milestone("exercises-$n", MilestoneKind.VARIETY, exercises(n), workout.startedAt, workout.workoutId)
                exerciseIndex += 1
            }
        }
        EXERCISE_COUNTS.getOrNull(exerciseIndex)?.let { n ->
            next += NextMilestone("exercises-$n", MilestoneKind.VARIETY, exercises(n), Format.count(seen.size), Format.count(n), seen.size / n.toDouble())
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

        // Busiest month: reached by the workout that brings a month's count up to the step.
        var bestMonth = 0
        ordered.groupBy { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(zone)) }.toSortedMap().forEach { (_, monthWorkouts) ->
            monthWorkouts.forEachIndexed { index, workout ->
                val n = index + 1
                if (n > bestMonth) {
                    MONTH_COUNTS.filter { it == n }.forEach { step ->
                        achieved += Milestone("month-$step", MilestoneKind.MONTH, month(step), workout.startedAt, workout.workoutId)
                    }
                    bestMonth = n
                }
            }
        }
        MONTH_COUNTS.firstOrNull { it > bestMonth }?.let { step ->
            val thisMonth = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
            val current = ordered.count { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(zone)) == thisMonth }
            next += NextMilestone("month-$step", MilestoneKind.MONTH, month(step), "$current", "$step", current / step.toDouble())
        }

        // Heaviest weight on the key lifts.
        KEY_LIFTS.forEach { (exerciseId, name) ->
            val liftSets = orderedSets.filter { it.exerciseId == exerciseId && (it.reps ?: 0) > 0 && (it.weightKg ?: 0.0) > 0.0 }
            if (liftSets.isEmpty()) return@forEach
            val (best, kg) = stepsReached(liftSets, { it.weightKg ?: 0.0 }, LIFT_KG, achieved, MilestoneKind.LIFT) { step ->
                "lift-$exerciseId-${step.toInt()}" to lift(name, step)
            }
            kg?.let {
                next += NextMilestone("lift-$exerciseId-${it.toInt()}", MilestoneKind.LIFT, lift(name, it), "${Format.number(best)} kg", "${Format.number(it)} kg", best / it)
            }
        }

        // The same lifts compared with body weight, once a body weight is logged.
        val weights = bodyWeights.sortedBy { it.epochDay }
        if (weights.isNotEmpty()) {
            KEY_LIFTS.forEach { (exerciseId, name) ->
                val liftSets = orderedSets.filter { it.exerciseId == exerciseId && (it.reps ?: 0) > 0 && (it.weightKg ?: 0.0) > 0.0 }
                if (liftSets.isEmpty()) return@forEach
                val ratioOf = { set: ProgressSet -> (set.weightKg ?: 0.0) / bodyWeightOn(weights, set.startedAt, zone) }
                val (best, step) = stepsReached(liftSets, ratioOf, STRENGTH_RATIOS, achieved, MilestoneKind.STRENGTH) { r ->
                    "strength-$exerciseId-${Math.round(r * 100)}" to strength(name, r)
                }
                step?.let {
                    val shown = floor(best * 100) / 100
                    next += NextMilestone("strength-$exerciseId-${Math.round(it * 100)}", MilestoneKind.STRENGTH, strength(name, it), ratio(shown), ratio(it), best / it)
                }
            }
        }

        // Reps in one set of the key body weight exercises.
        val repSteps = SET_REPS.map { it.toDouble() }
        KEY_REP_EXERCISES.forEach { (name, ids) ->
            val repSets = orderedSets.filter { it.exerciseId in ids && (it.reps ?: 0) > 0 }
            if (repSets.isEmpty()) return@forEach
            val (best, step) = stepsReached(repSets, { (it.reps ?: 0).toDouble() }, repSteps, achieved, MilestoneKind.REPS) { n ->
                "reps-$name-${n.toInt()}" to reps(name, n.toInt())
            }
            step?.let {
                next += NextMilestone("reps-$name-${it.toInt()}", MilestoneKind.REPS, reps(name, it.toInt()), "${best.toInt()}", "${it.toInt()}", best / it)
            }
        }

        return MilestoneReport(
            achieved = achieved.sortedWith(compareByDescending<Milestone> { it.achievedAt }.thenByDescending { it.kind.ordinal }),
            next = next.sortedByDescending { it.fraction },
        )
    }

    /** Hours from start to finish, capped at [MAX_COUNTED_MS]; 0 while unknown. */
    private fun durationHours(workout: WorkoutStart): Double {
        val ended = workout.endedAt ?: return 0.0
        return (ended - workout.startedAt).coerceIn(0L, MAX_COUNTED_MS) / 3_600_000.0
    }

    /** The body weight logged on or before the set's day; before the first entry, the first entry. */
    private fun bodyWeightOn(weights: List<BodyWeight>, timeMs: Long, zone: ZoneId): Double {
        val day = Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate().toEpochDay()
        return (weights.lastOrNull { it.epochDay <= day } ?: weights.first()).weightKg
    }

    /**
     * Adds up [value] over [workouts] in order; each step in [steps] is reached by the workout that
     * takes the total to it. Returns the total and the next step (null when all are reached).
     */
    private fun runningTotal(
        workouts: List<WorkoutStart>,
        value: (WorkoutStart) -> Double,
        steps: List<Double>,
        achieved: MutableList<Milestone>,
        kind: MilestoneKind = MilestoneKind.VOLUME,
        name: (Double) -> Pair<String, String>,
    ): Pair<Double, Double?> {
        var total = 0.0
        var index = 0
        workouts.forEach { workout ->
            total += value(workout)
            while (index < steps.size && total >= steps[index]) {
                val (key, title) = name(steps[index])
                achieved += Milestone(key, kind, title, workout.startedAt, workout.workoutId)
                index += 1
            }
        }
        return total to steps.getOrNull(index)
    }

    /**
     * Walks [sets] in date order keeping the best [value]; each step is reached by the first set at
     * or above it. Returns the best value and the next step (null when all are reached).
     */
    private fun stepsReached(
        sets: List<ProgressSet>,
        value: (ProgressSet) -> Double,
        steps: List<Double>,
        achieved: MutableList<Milestone>,
        kind: MilestoneKind,
        name: (Double) -> Pair<String, String>,
    ): Pair<Double, Double?> {
        var best = 0.0
        var index = 0
        sets.forEach { set ->
            best = maxOf(best, value(set))
            while (index < steps.size && best >= steps[index] - 1e-9) {
                val (key, title) = name(steps[index])
                achieved += Milestone(key, kind, title, set.startedAt, set.workoutId)
                index += 1
            }
        }
        return best to steps.getOrNull(index)
    }
}
