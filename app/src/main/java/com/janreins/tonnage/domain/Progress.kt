package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.ProgressSet
import com.janreins.tonnage.data.model.TrackingType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What an exercise's progress chart can show. */
enum class ProgressMetric(val label: String) {
    ONE_REP_MAX("Est. 1RM"),
    HEAVIEST("Heaviest"),
    VOLUME("Volume"),
    MOST_REPS("Most reps"),
    TOTAL_REPS("Total reps"),
    LONGEST("Longest"),
    DISTANCE("Distance"),
    TOTAL_TIME("Time"),
    ;

    fun format(value: Double): String = when (this) {
        ONE_REP_MAX, HEAVIEST -> "${Format.number(Math.round(value * 10) / 10.0)} kg"
        VOLUME -> Format.volume(value)
        MOST_REPS, TOTAL_REPS -> "${value.toInt()} reps"
        LONGEST, TOTAL_TIME -> Format.duration(value.toLong() * 1000)
        DISTANCE -> "${Format.number(Math.round(value * 100) / 100.0)} km"
    }

    companion object {
        /** The metrics charted for each way of logging, the headline one first. */
        fun forType(type: TrackingType): List<ProgressMetric> = when (type) {
            TrackingType.WEIGHT_REPS -> listOf(ONE_REP_MAX, HEAVIEST, VOLUME)
            TrackingType.BODYWEIGHT_REPS -> listOf(MOST_REPS, TOTAL_REPS)
            TrackingType.DURATION -> listOf(LONGEST, TOTAL_TIME)
            TrackingType.DISTANCE_DURATION -> listOf(DISTANCE, TOTAL_TIME)
        }
    }
}

/** One workout's sets of one exercise, boiled down to the numbers the chart plots. */
data class ExerciseSession(
    val workoutId: Long,
    val startedAt: Long,
    val sets: Int,
    val values: Map<ProgressMetric, Double>,
    /** The best set by the headline metric, e.g. "60 kg × 8". */
    val bestSet: String,
)

/** An exercise that appears in history, for the Progress tab's list. */
data class ExerciseProgress(
    val exerciseId: String,
    val name: String,
    val trackingType: TrackingType,
    val sessions: Int,
    val lastStartedAt: Long,
    /** Best ever value of the headline metric. */
    val best: Double,
)

/** Totals for one week (Monday to Sunday). */
data class WeekStats(val weekStart: LocalDate, val workouts: Int, val sets: Int, val volumeKg: Double)

/** Weeks in a row with at least one workout. */
data class Streak(val current: Int, val longest: Int)

data class MuscleSets(val muscle: String, val sets: Int)

object Progress {

    const val OTHER_MUSCLE = "other"

    fun trackingTypeOf(set: ProgressSet): TrackingType =
        TrackingType.entries.firstOrNull { it.name == set.trackingType } ?: TrackingType.WEIGHT_REPS

    private fun value(metric: ProgressMetric, sets: List<ProgressSet>): Double = when (metric) {
        ProgressMetric.ONE_REP_MAX -> sets.maxOf { WorkoutStats.estimatedOneRepMax(it.weightKg ?: 0.0, it.reps ?: 0) }
        ProgressMetric.HEAVIEST -> sets.filter { (it.reps ?: 0) > 0 }.maxOfOrNull { it.weightKg ?: 0.0 } ?: 0.0
        ProgressMetric.VOLUME -> sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
        ProgressMetric.MOST_REPS -> sets.maxOf { it.reps ?: 0 }.toDouble()
        ProgressMetric.TOTAL_REPS -> sets.sumOf { it.reps ?: 0 }.toDouble()
        ProgressMetric.LONGEST -> sets.maxOf { it.durationSec ?: 0 }.toDouble()
        ProgressMetric.DISTANCE -> sets.sumOf { it.distanceKm ?: 0.0 }
        ProgressMetric.TOTAL_TIME -> sets.sumOf { it.durationSec ?: 0 }.toDouble()
    }

    private fun setValue(metric: ProgressMetric, set: ProgressSet): Double = value(metric, listOf(set))

    private fun describe(set: ProgressSet, type: TrackingType): String = when (type) {
        TrackingType.WEIGHT_REPS -> "${Format.number(set.weightKg ?: 0.0)} kg × ${set.reps ?: 0}"
        TrackingType.BODYWEIGHT_REPS -> "${set.reps ?: 0} reps"
        TrackingType.DURATION -> Format.duration((set.durationSec ?: 0) * 1000L)
        TrackingType.DISTANCE_DURATION -> listOfNotNull(
            set.distanceKm?.let { "${Format.number(it)} km" },
            set.durationSec?.let { Format.duration(it * 1000L) },
        ).joinToString(" in ")
    }

    /** Every workout that has [exerciseId], oldest first, skipping ones with nothing to plot. */
    fun sessions(sets: List<ProgressSet>, exerciseId: String): List<ExerciseSession> {
        val mine = sets.filter { it.exerciseId == exerciseId }
        if (mine.isEmpty()) return emptyList()
        val type = trackingTypeOf(mine.first())
        val metrics = ProgressMetric.forType(type)
        return mine.groupBy { it.workoutId }.values
            .map { workoutSets ->
                val headline = metrics.first()
                ExerciseSession(
                    workoutId = workoutSets.first().workoutId,
                    startedAt = workoutSets.first().startedAt,
                    sets = workoutSets.size,
                    values = metrics.associateWith { value(it, workoutSets) },
                    bestSet = describe(workoutSets.maxBy { setValue(headline, it) }, type),
                )
            }
            .filter { session -> session.values.values.any { it > 0.0 } }
            .sortedBy { it.startedAt }
    }

    const val RECENT_SESSIONS = 10

    /** The newest [RECENT_SESSIONS] of [sessions] (oldest first), newest first. */
    fun newestSessions(sessions: List<ExerciseSession>): List<ExerciseSession> =
        sessions.takeLast(RECENT_SESSIONS).reversed()

    /**
     * How many sessions (oldest first) came after the latest one that set a new best of [metric],
     * meaning strictly higher than every earlier session. The first session counts as a best.
     */
    fun sessionsSinceBest(sessions: List<ExerciseSession>, metric: ProgressMetric): Int {
        var best = Double.NEGATIVE_INFINITY
        var lastBest = -1
        sessions.forEachIndexed { index, session ->
            val value = session.values[metric] ?: 0.0
            if (index == 0 || value > best) lastBest = index
            best = maxOf(best, value)
        }
        return if (lastBest < 0) 0 else sessions.lastIndex - lastBest
    }

    /** Exercises done in finished workouts, most recently done first. */
    fun exercises(sets: List<ProgressSet>): List<ExerciseProgress> =
        sets.groupBy { it.exerciseId }.map { (exerciseId, exerciseSets) ->
            val type = trackingTypeOf(exerciseSets.first())
            val headline = ProgressMetric.forType(type).first()
            val byWorkout = exerciseSets.groupBy { it.workoutId }.values
            ExerciseProgress(
                exerciseId = exerciseId,
                name = exerciseSets.first().exerciseName ?: "Unknown exercise",
                trackingType = type,
                sessions = byWorkout.size,
                lastStartedAt = exerciseSets.maxOf { it.startedAt },
                best = byWorkout.maxOf { value(headline, it) },
            )
        }.sortedWith(compareByDescending<ExerciseProgress> { it.lastStartedAt }.thenBy { it.name })

    /** The Monday of the week [timeMs] falls in. */
    fun weekStart(timeMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * The last [count] weeks up to and including the one [now] is in, oldest first. Workouts are
     * counted from [workoutStarts] (so a workout with no sets still counts); sets and volume
     * come from [sets].
     */
    fun weeks(
        workoutStarts: List<Long>,
        sets: List<ProgressSet>,
        now: Long,
        count: Int = 12,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<WeekStats> {
        val thisWeek = weekStart(now, zone)
        val workoutsByWeek = workoutStarts.groupingBy { weekStart(it, zone) }.eachCount()
        val setsByWeek = sets.groupBy { weekStart(it.startedAt, zone) }
        return (count - 1 downTo 0).map { back ->
            val week = thisWeek.minusWeeks(back.toLong())
            val weekSets = setsByWeek[week].orEmpty()
            WeekStats(
                weekStart = week,
                workouts = workoutsByWeek[week] ?: 0,
                sets = weekSets.size,
                volumeKg = weekSets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
            )
        }
    }

    /**
     * Working sets per main muscle in the week starting [weekStart], most worked first. A set of an
     * exercise with two main muscles counts for both; exercises without muscles count as "other".
     */
    fun muscles(sets: List<ProgressSet>, weekStart: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<MuscleSets> =
        sets.filter { weekStart(it.startedAt, zone) == weekStart }
            .flatMap { set ->
                set.primaryMuscles.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf(OTHER_MUSCLE) }
            }
            .groupingBy { it }.eachCount()
            .map { (muscle, count) -> MuscleSets(muscle, count) }
            .sortedWith(compareByDescending<MuscleSets> { it.sets }.thenBy { it.muscle })

    /**
     * The current run of weeks with a workout, and the longest ever. The current run still counts
     * while this week has no workout yet, as long as last week had one.
     */
    fun streak(workoutStarts: List<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Streak {
        val weeks = workoutStarts.map { weekStart(it, zone) }.toSortedSet()
        if (weeks.isEmpty()) return Streak(0, 0)
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (week in weeks) {
            val before = previous
            run = if (before != null && ChronoUnit.WEEKS.between(before, week) == 1L) run + 1 else 1
            longest = maxOf(longest, run)
            previous = week
        }
        val thisWeek = weekStart(now, zone)
        val last = weeks.last()
        val current = if (last == thisWeek || last == thisWeek.minusWeeks(1)) run else 0
        return Streak(current, longest)
    }
}
