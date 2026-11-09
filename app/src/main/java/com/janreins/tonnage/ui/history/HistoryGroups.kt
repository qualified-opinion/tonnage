package com.janreins.tonnage.ui.history

import com.janreins.tonnage.data.model.WorkoutSummary
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One calendar month of the history list, newest workout first, with its totals. */
data class MonthGroup(val month: YearMonth, val workouts: List<WorkoutSummary>) {
    val volumeKg: Double get() = workouts.sumOf { it.volumeKg }
}

object HistoryGroups {
    /**
     * Keeps the workouts whose title or exercise names contain every word of [query] (any case),
     * then groups them by calendar month in [zone], keeping the list's newest-first order.
     */
    fun group(
        workouts: List<WorkoutSummary>,
        exerciseNames: Map<Long, List<String>>,
        query: String,
        zone: ZoneId,
    ): List<MonthGroup> {
        val words = query.lowercase().split(' ').map { it.trim() }.filter { it.isNotEmpty() }
        val shown = if (words.isEmpty()) {
            workouts
        } else {
            workouts.filter { workout ->
                val text = (listOf(workout.title) + exerciseNames[workout.id].orEmpty()).joinToString(" ").lowercase()
                words.all { it in text }
            }
        }
        return shown
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(zone)) }
            .map { (month, list) -> MonthGroup(month, list) }
    }
}
