package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.RoutineSummary
import com.janreins.tonnage.data.model.RoutineWorkoutTime

/** When each routine was last done and how long it usually takes, worked out from finished workouts. */
object RoutineStats {
    /** How many of a routine's latest workouts its typical length is taken from. */
    const val RECENT = 5

    data class Stats(val lastStartedAt: Long, val typicalDurationMs: Long)

    /**
     * Per routine: the latest start, and the median length of its last [RECENT] workouts, so one
     * workout left running for hours doesn't skew it.
     */
    fun byRoutine(times: List<RoutineWorkoutTime>): Map<Long, Stats> =
        times.groupBy { it.routineId }.mapValues { (_, workouts) ->
            val recent = workouts.sortedByDescending { it.startedAt }.take(RECENT)
            Stats(recent.first().startedAt, median(recent.map { (it.endedAt - it.startedAt).coerceAtLeast(0) }))
        }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /**
     * The routine to suggest next: of the routines with exercises that have been done, the one
     * done longest ago. Only when at least two have been done, otherwise there's nothing to rotate.
     */
    fun upNext(routines: List<RoutineSummary>): Long? {
        val done = routines.filter { it.lastDoneAt != null && it.exerciseNames.isNotEmpty() }
        if (done.size < 2) return null
        return done.minByOrNull { it.lastDoneAt!! }?.routine?.id
    }
}
