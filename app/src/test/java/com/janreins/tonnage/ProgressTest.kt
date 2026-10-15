package com.janreins.tonnage

import com.janreins.tonnage.data.model.ProgressSet
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.domain.MuscleSets
import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.domain.ProgressMetric
import com.janreins.tonnage.domain.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ProgressTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun at(date: String, hour: Int = 9): Long =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun set(
        workoutId: Long,
        date: String,
        exerciseId: String = "bench",
        weight: Double? = null,
        reps: Int? = null,
        type: TrackingType = TrackingType.WEIGHT_REPS,
        muscles: String? = "chest,triceps",
        km: Double? = null,
        seconds: Int? = null,
    ) = ProgressSet(workoutId, at(date), exerciseId, exerciseId.replaceFirstChar { it.uppercase() }, type.name, muscles, weight, reps, km, seconds)

    @Test
    fun weeksStartOnMonday() {
        assertEquals(LocalDate.parse("2026-10-12"), Progress.weekStart(at("2026-10-12"), zone)) // Monday
        assertEquals(LocalDate.parse("2026-10-12"), Progress.weekStart(at("2026-10-18", hour = 23), zone)) // Sunday night
        assertEquals(LocalDate.parse("2026-10-19"), Progress.weekStart(at("2026-10-19", hour = 0), zone))
    }

    @Test
    fun sessionsChartTheBestOfEachWorkout() {
        val sets = listOf(
            set(1, "2026-09-01", weight = 50.0, reps = 10),
            set(1, "2026-09-01", weight = 60.0, reps = 5),
            set(2, "2026-09-08", weight = 62.5, reps = 3),
            set(2, "2026-09-08", exerciseId = "squat", weight = 80.0, reps = 5),
        )
        val sessions = Progress.sessions(sets, "bench")

        assertEquals(listOf(1L, 2L), sessions.map { it.workoutId })
        val first = sessions[0]
        assertEquals(2, first.sets)
        assertEquals(70.0, first.values.getValue(ProgressMetric.ONE_REP_MAX), 0.001) // 60 × (1 + 5/30)
        assertEquals(60.0, first.values.getValue(ProgressMetric.HEAVIEST), 0.0)
        assertEquals(800.0, first.values.getValue(ProgressMetric.VOLUME), 0.0)
        assertEquals("60 kg × 5", first.bestSet)
        assertEquals(68.75, sessions[1].values.getValue(ProgressMetric.ONE_REP_MAX), 0.001)
    }

    @Test
    fun cardioAndBodyweightChartTheirOwnNumbers() {
        val walks = listOf(
            set(1, "2026-07-13", exerciseId = "walk", type = TrackingType.DISTANCE_DURATION, muscles = null, km = 7.0, seconds = 3000),
            set(2, "2026-07-20", exerciseId = "walk", type = TrackingType.DISTANCE_DURATION, muscles = null, km = 2.5, seconds = 900),
        )
        val sessions = Progress.sessions(walks, "walk")
        assertEquals(listOf(7.0, 2.5), sessions.map { it.values.getValue(ProgressMetric.DISTANCE) })
        assertEquals("7 km in 50:00", sessions[0].bestSet)

        val pushUps = listOf(set(3, "2026-07-20", exerciseId = "pushups", type = TrackingType.BODYWEIGHT_REPS, reps = 15), set(3, "2026-07-20", exerciseId = "pushups", type = TrackingType.BODYWEIGHT_REPS, reps = 12))
        val session = Progress.sessions(pushUps, "pushups").single()
        assertEquals(15.0, session.values.getValue(ProgressMetric.MOST_REPS), 0.0)
        assertEquals(27.0, session.values.getValue(ProgressMetric.TOTAL_REPS), 0.0)
        assertEquals(listOf(ProgressMetric.MOST_REPS, ProgressMetric.TOTAL_REPS), ProgressMetric.forType(TrackingType.BODYWEIGHT_REPS))
    }

    @Test
    fun exerciseListIsMostRecentFirstWithTheBestHeadlineValue() {
        val sets = listOf(
            set(1, "2026-09-01", weight = 60.0, reps = 5),
            set(2, "2026-09-08", exerciseId = "squat", weight = 80.0, reps = 1),
            set(3, "2026-09-15", weight = 50.0, reps = 5),
        )
        val list = Progress.exercises(sets)
        assertEquals(listOf("bench", "squat"), list.map { it.exerciseId })
        assertEquals(2, list[0].sessions)
        assertEquals(70.0, list[0].best, 0.001)
        assertEquals(80.0, list[1].best, 0.0)
    }

    @Test
    fun weeksCountWorkoutsSetsAndVolume() {
        val starts = listOf(at("2026-09-28"), at("2026-10-01"), at("2026-10-13"))
        val sets = listOf(
            set(1, "2026-09-28", weight = 50.0, reps = 10),
            set(2, "2026-10-01", weight = 60.0, reps = 5),
            set(2, "2026-10-01", weight = 60.0, reps = 5),
        )
        val weeks = Progress.weeks(starts, sets, now = at("2026-10-15"), count = 4, zone = zone)

        assertEquals(listOf("2026-09-21", "2026-09-28", "2026-10-05", "2026-10-12"), weeks.map { it.weekStart.toString() })
        assertEquals(listOf(0, 2, 0, 1), weeks.map { it.workouts })
        assertEquals(listOf(0, 3, 0, 0), weeks.map { it.sets })
        assertEquals(1100.0, weeks[1].volumeKg, 0.0)
    }

    @Test
    fun musclesCountEachMainMuscleOfASet() {
        val sets = listOf(
            set(1, "2026-10-13", weight = 60.0, reps = 5),
            set(1, "2026-10-13", weight = 60.0, reps = 5),
            set(1, "2026-10-13", exerciseId = "squat", weight = 80.0, reps = 5, muscles = "quadriceps"),
            set(1, "2026-10-13", exerciseId = "walk", type = TrackingType.DISTANCE_DURATION, muscles = "", km = 2.0),
            set(2, "2026-10-01", exerciseId = "squat", weight = 80.0, reps = 5, muscles = "quadriceps"),
        )
        assertEquals(
            listOf(MuscleSets("chest", 2), MuscleSets("triceps", 2), MuscleSets("other", 1), MuscleSets("quadriceps", 1)),
            Progress.muscles(sets, LocalDate.parse("2026-10-12"), zone),
        )
    }

    @Test
    fun streaksCountWeeksInARow() {
        // Weeks of 1, 8 and 15 Jun, a gap, then 6 and 13 Jul.
        val starts = listOf("2026-06-03", "2026-06-10", "2026-06-12", "2026-06-17", "2026-07-08", "2026-07-15").map { at(it) }

        // Still running the week after (nothing logged yet this week), broken a week later.
        assertEquals(Streak(current = 2, longest = 3), Progress.streak(starts, now = at("2026-07-21"), zone = zone))
        assertEquals(Streak(current = 0, longest = 3), Progress.streak(starts, now = at("2026-07-28"), zone = zone))
        assertEquals(Streak(0, 0), Progress.streak(emptyList(), now = at("2026-07-28"), zone = zone))
    }

    @Test
    fun metricsFormatForReading() {
        assertEquals("70.4 kg", ProgressMetric.ONE_REP_MAX.format(70.4166))
        assertEquals("1,250 kg", ProgressMetric.VOLUME.format(1250.0))
        assertEquals("15 reps", ProgressMetric.MOST_REPS.format(15.0))
        assertEquals("50:00", ProgressMetric.TOTAL_TIME.format(3000.0))
        assertTrue(ProgressMetric.DISTANCE.format(7.0).startsWith("7 km"))
    }
}
