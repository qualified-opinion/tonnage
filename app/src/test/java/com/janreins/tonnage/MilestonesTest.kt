package com.janreins.tonnage

import com.janreins.tonnage.data.model.BodyWeight
import com.janreins.tonnage.data.model.ProgressSet
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.domain.MilestoneKind
import com.janreins.tonnage.domain.Milestones
import com.janreins.tonnage.domain.WorkoutStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MilestonesTest {

    private val zone = ZoneOffset.UTC
    private fun at(date: String): Long = LocalDate.parse(date).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun bench(workoutId: Long, date: String, weight: Double, reps: Int = 5) = ProgressSet(
        workoutId, at(date), "Barbell_Bench_Press_-_Medium_Grip", "Barbell Bench Press - Medium Grip",
        TrackingType.WEIGHT_REPS.name, "chest", weight, reps, null, null,
    )

    /** One workout a week from 1 Jun 2026 (a Monday), ids 1..n. */
    private fun weekly(n: Int) = (1..n).map { WorkoutStart(it.toLong(), at(LocalDate.parse("2026-06-01").plusWeeks(it - 1L).toString())) }

    @Test
    fun workoutCountsAreReachedByTheRightWorkout() {
        val workouts = weekly(12)
        val report = Milestones.report(workouts, emptyList(), now = at("2026-08-20"), zone = zone)
        val counts = report.achieved.filter { it.kind == MilestoneKind.WORKOUTS }

        assertEquals(listOf("10 workouts", "5 workouts", "First workout"), counts.map { it.title })
        assertEquals(listOf(10L, 5L, 1L), counts.map { it.workoutId })
        val next = report.next.single { it.kind == MilestoneKind.WORKOUTS }
        assertEquals("25 workouts", next.title)
        assertEquals(12 / 25.0, next.fraction, 0.0)
    }

    @Test
    fun totalWeightCountsWorkingSetsAcrossWorkouts() {
        val workouts = weekly(3)
        // 6,000 kg, then 3,000 kg, then 2,000 kg: 10,000 kg is crossed in the third workout.
        val sets = listOf(bench(1, "2026-06-01", 100.0, 60), bench(2, "2026-06-08", 100.0, 30), bench(3, "2026-06-15", 100.0, 20))
        val report = Milestones.report(workouts, sets, now = at("2026-06-16"), zone = zone)

        val volume = report.achieved.single { it.kind == MilestoneKind.VOLUME }
        assertEquals("10,000 kg lifted", volume.title)
        assertEquals(3L, volume.workoutId)
        val next = report.next.single { it.kind == MilestoneKind.VOLUME }
        assertEquals("11,000 kg", next.current)
        assertEquals("25,000 kg", next.target)
    }

    @Test
    fun streaksAreReachedOnceAndBreaksDoNotRepeatThem() {
        // Four weeks in a row, a gap, then four more: the 2- and 4-week streaks are reached once.
        val dates = listOf("2026-06-01", "2026-06-08", "2026-06-15", "2026-06-22", "2026-07-13", "2026-07-20", "2026-07-27", "2026-08-03")
        val workouts = dates.mapIndexed { i, d -> WorkoutStart(i + 1L, at(d)) }
        val report = Milestones.report(workouts, emptyList(), now = at("2026-08-05"), zone = zone)

        val streaks = report.achieved.filter { it.kind == MilestoneKind.STREAK }
        assertEquals(listOf("4-week streak", "2-week streak"), streaks.map { it.title })
        assertEquals(listOf(4L, 2L), streaks.map { it.workoutId })
        val next = report.next.single { it.kind == MilestoneKind.STREAK }
        assertEquals("8-week streak", next.title)
        assertEquals("4", next.current)
        assertEquals(0.5, next.fraction, 0.0)
    }

    @Test
    fun keyLiftsReachEveryStepTheyPass() {
        val workouts = weekly(2)
        val sets = listOf(bench(1, "2026-06-01", 50.0), bench(2, "2026-06-08", 82.5), bench(2, "2026-06-08", 85.0, reps = 0))
        val report = Milestones.report(workouts, sets, now = at("2026-06-09"), zone = zone)

        val lifts = report.achieved.filter { it.kind == MilestoneKind.LIFT }
        assertEquals(setOf("Bench press 40 kg", "Bench press 60 kg", "Bench press 80 kg"), lifts.map { it.title }.toSet())
        assertEquals(1L, lifts.single { it.title == "Bench press 40 kg" }.workoutId)
        val next = report.next.single { it.kind == MilestoneKind.LIFT }
        assertEquals("Bench press 100 kg", next.title)
        assertEquals("82.5 kg", next.current)
        // Lifts not done yet have no milestones at all.
        assertTrue(report.achieved.none { it.title.startsWith("Squat") } && report.next.none { it.title.startsWith("Squat") })
    }

    @Test
    fun nothingLoggedMeansOnlyAFirstWorkoutToAimFor() {
        val report = Milestones.report(emptyList(), emptyList(), now = at("2026-06-09"), zone = zone)
        assertTrue(report.achieved.isEmpty())
        assertEquals(
            setOf("First workout", "10,000 kg lifted", "10 hours trained", "10 different exercises", "2-week streak", "4 workouts in a month"),
            report.next.map { it.title }.toSet(),
        )
    }

    private fun set(workoutId: Long, date: String, exerciseId: String, weight: Double?, reps: Int) = ProgressSet(
        workoutId, at(date), exerciseId, exerciseId, TrackingType.WEIGHT_REPS.name, "chest", weight, reps, null, null,
    )

    @Test
    fun strengthMilestonesUseTheBodyWeightLoggedByThatDay() {
        val workouts = weekly(2)
        val weights = listOf(
            BodyWeight(epochDay = LocalDate.parse("2026-05-20").toEpochDay(), weightKg = 80.0, createdAt = 0),
            BodyWeight(epochDay = LocalDate.parse("2026-06-05").toEpochDay(), weightKg = 100.0, createdAt = 0),
        )
        // 80 kg at 80 kg body weight is 1×; 100 kg a week later at 100 kg body weight is still 1×.
        val sets = listOf(bench(1, "2026-06-01", 80.0), bench(2, "2026-06-08", 100.0))
        val report = Milestones.report(workouts, sets, now = at("2026-06-09"), zone = zone, bodyWeights = weights)

        val strength = report.achieved.filter { it.kind == MilestoneKind.STRENGTH }
        assertEquals(setOf("Bench press 0.5× body weight", "Bench press 0.75× body weight", "Bench press 1× body weight"), strength.map { it.title }.toSet())
        assertTrue(strength.all { it.workoutId == 1L })
        val next = report.next.single { it.kind == MilestoneKind.STRENGTH }
        assertEquals("Bench press 1.25× body weight", next.title)
        assertEquals("1×", next.current)
        // Without a body weight there are no strength milestones at all.
        assertTrue(Milestones.report(workouts, sets, now = at("2026-06-09"), zone = zone).next.none { it.kind == MilestoneKind.STRENGTH })
    }

    @Test
    fun repMilestonesCountTheBestSetOfEachKeyExercise() {
        val workouts = weekly(2)
        val sets = listOf(
            set(1, "2026-06-01", "Pullups", null, 6),
            set(2, "2026-06-08", "Pullups", null, 11),
            set(2, "2026-06-08", "Parallel_Bar_Dip", null, 5),
        )
        val report = Milestones.report(workouts, sets, now = at("2026-06-09"), zone = zone)

        val reps = report.achieved.filter { it.kind == MilestoneKind.REPS }
        assertEquals(setOf("5 pull-ups in one set", "10 pull-ups in one set", "5 dips in one set"), reps.map { it.title }.toSet())
        assertEquals(1L, reps.single { it.title == "5 pull-ups in one set" }.workoutId)
        assertEquals(setOf("15 pull-ups in one set", "10 dips in one set"), report.next.filter { it.kind == MilestoneKind.REPS }.map { it.title }.toSet())
    }

    @Test
    fun hoursTrainedCapEachWorkoutAtFourHours() {
        // Three 2-hour workouts and one left running for 20 hours (counted as 4): 10 hours in the fourth.
        val hour = 3_600_000L
        val workouts = weekly(4).mapIndexed { i, w -> w.copy(endedAt = w.startedAt + if (i == 3) 20 * hour else 2 * hour) }
        val report = Milestones.report(workouts, emptyList(), now = at("2026-06-30"), zone = zone)

        assertEquals(4L, report.achieved.single { it.kind == MilestoneKind.TIME }.workoutId)
        val next = report.next.single { it.kind == MilestoneKind.TIME }
        assertEquals("25 hours trained", next.title)
        assertEquals("10 h", next.current)
    }

    @Test
    fun differentExercisesAreCountedOnce() {
        val workouts = weekly(2)
        val first = (1..6).map { set(1, "2026-06-01", "Exercise_$it", 20.0, 5) }
        val second = (4..10).map { set(2, "2026-06-08", "Exercise_$it", 20.0, 5) }
        val report = Milestones.report(workouts, first + second, now = at("2026-06-09"), zone = zone)

        val variety = report.achieved.single { it.kind == MilestoneKind.VARIETY }
        assertEquals("10 different exercises", variety.title)
        assertEquals(2L, variety.workoutId)
    }

    @Test
    fun busiestMonthIsReachedByTheWorkoutThatFillsIt() {
        // Five workouts in June, then eight in July: 4 is reached in June, 8 by July's eighth workout.
        val june = (1..5).map { WorkoutStart(it.toLong(), at("2026-06-${(it * 3).toString().padStart(2, '0')}")) }
        val july = (1..8).map { WorkoutStart(10L + it, at("2026-07-${(it * 3).toString().padStart(2, '0')}")) }
        val report = Milestones.report(june + july, emptyList(), now = at("2026-07-25"), zone = zone)

        val months = report.achieved.filter { it.kind == MilestoneKind.MONTH }
        assertEquals(listOf("8 workouts in a month", "4 workouts in a month"), months.map { it.title })
        assertEquals(listOf(18L, 4L), months.map { it.workoutId })
        val next = report.next.single { it.kind == MilestoneKind.MONTH }
        assertEquals("12 workouts in a month", next.title)
        assertEquals("8", next.current)
    }
}
