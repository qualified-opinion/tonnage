package com.janreins.tonnage

import com.janreins.tonnage.data.model.Routine
import com.janreins.tonnage.data.model.RoutineSummary
import com.janreins.tonnage.data.model.RoutineWorkoutTime
import com.janreins.tonnage.data.model.WorkoutSummary
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.RoutineStats
import com.janreins.tonnage.ui.history.HistoryGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class UiHelpersTest {
    private val minute = 60_000L

    @Test fun typicalRoutineLengthIsTheMedianOfTheLastFive() {
        val times = listOf(
            RoutineWorkoutTime(1, startedAt = 0, endedAt = 400 * minute), // oldest, left running for hours: dropped
            RoutineWorkoutTime(1, startedAt = 1_000 * minute, endedAt = 1_050 * minute),
            RoutineWorkoutTime(1, startedAt = 2_000 * minute, endedAt = 2_060 * minute),
            RoutineWorkoutTime(1, startedAt = 3_000 * minute, endedAt = 3_300 * minute), // forgotten, still in the five
            RoutineWorkoutTime(1, startedAt = 4_000 * minute, endedAt = 4_055 * minute),
            RoutineWorkoutTime(1, startedAt = 5_000 * minute, endedAt = 5_058 * minute),
            RoutineWorkoutTime(2, startedAt = 100 * minute, endedAt = 140 * minute),
            RoutineWorkoutTime(2, startedAt = 200 * minute, endedAt = 250 * minute),
        )
        val stats = RoutineStats.byRoutine(times)
        assertEquals(5_000 * minute, stats.getValue(1).lastStartedAt)
        assertEquals(58 * minute, stats.getValue(1).typicalDurationMs) // 50, 55, 58, 60, 300
        assertEquals(45 * minute, stats.getValue(2).typicalDurationMs)
    }

    private fun summary(id: Long, lastDoneAt: Long?, exercises: Int = 3) =
        RoutineSummary(Routine(id = id, name = "R$id"), List(exercises) { "E$it" }, lastDoneAt = lastDoneAt)

    @Test fun upNextIsTheDoneRoutineDoneLongestAgo() {
        assertEquals(2L, RoutineStats.upNext(listOf(summary(1, 500), summary(2, 100), summary(3, null), summary(4, 50, exercises = 0))))
    }

    @Test fun noUpNextUntilTwoRoutinesHaveBeenDone() {
        assertNull(RoutineStats.upNext(listOf(summary(1, 500), summary(2, null))))
        assertNull(RoutineStats.upNext(emptyList()))
    }

    @Test fun roughDurationRoundsToFiveMinutes() {
        assertEquals("5 min", Format.roughDuration(0))
        assertEquals("45 min", Format.roughDuration(46 * minute))
        assertEquals("1 h", Format.roughDuration(58 * minute))
        assertEquals("1 h 15 min", Format.roughDuration(73 * minute))
    }

    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(year: Int, month: Int, day: Int) = LocalDateTime.of(year, month, day, 18, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun workout(id: Long, title: String, startedAt: Long, volume: Double = 100.0) =
        WorkoutSummary(id, title, startedAt, startedAt + 3_600_000, exerciseCount = 1, setCount = 3, volumeKg = volume)

    @Test fun historyGroupsByMonthNewestFirstWithTotals() {
        val workouts = listOf(
            workout(3, "Legs", at(2026, 11, 7), 900.0),
            workout(2, "Pull", at(2026, 11, 5), 600.0),
            workout(1, "Push", at(2026, 10, 30), 500.0),
        )
        val groups = HistoryGroups.group(workouts, emptyMap(), "", zone)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), groups.map { it.month })
        assertEquals(listOf(3L, 2L), groups[0].workouts.map { it.id })
        assertEquals(1_500.0, groups[0].volumeKg, 0.0)
    }

    @Test fun historySearchMatchesTitleOrExerciseNamesWordByWord() {
        val workouts = listOf(workout(2, "Pull A", at(2026, 11, 5)), workout(1, "Push A", at(2026, 11, 3)))
        val names = mapOf(2L to listOf("Barbell Row", "Pull-ups"), 1L to listOf("Bench Press", "Barbell Overhead Press"))
        fun ids(query: String) = HistoryGroups.group(workouts, names, query, zone).flatMap { it.workouts }.map { it.id }
        assertEquals(listOf(2L), ids("row"))
        assertEquals(listOf(1L), ids("PUSH"))
        assertEquals(listOf(1L), ids("barbell press"))
        assertEquals(listOf(2L, 1L), ids("barbell"))
        assertEquals(emptyList<Long>(), ids("squat"))
    }
}
