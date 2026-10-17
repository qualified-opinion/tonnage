package com.janreins.tonnage

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.PastSet
import com.janreins.tonnage.data.model.RecordSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.domain.PrKind
import com.janreins.tonnage.domain.WorkoutStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStatsTest {

    private val bench = Exercise(id = "bench", name = "Bench Press", category = "strength", equipment = "barbell", primaryMuscles = "chest")
    private val pullup = Exercise(
        id = "pullup", name = "Pullups", category = "strength", equipment = "body only", primaryMuscles = "lats",
        trackingType = TrackingType.BODYWEIGHT_REPS,
    )

    private var nextSetId = 1L

    private fun set(weight: Double?, reps: Int?, completed: Boolean = true, type: SetType = SetType.NORMAL) =
        WorkoutSet(id = nextSetId++, workoutExerciseId = 0, position = 0, type = type, weightKg = weight, reps = reps, completed = completed)

    private fun block(exercise: Exercise, id: Long, vararg sets: WorkoutSet) = ExerciseBlock(
        WorkoutExercise(id = id, workoutId = 1, exerciseId = exercise.id, position = id.toInt(), restSeconds = 90),
        exercise,
        sets.toList(),
    )

    @Test
    fun epleyEstimate() {
        assertEquals(100.0, WorkoutStats.estimatedOneRepMax(100.0, 1), 0.001)
        assertEquals(133.333, WorkoutStats.estimatedOneRepMax(100.0, 10), 0.001)
        assertEquals(0.0, WorkoutStats.estimatedOneRepMax(100.0, 0), 0.0)
    }

    @Test
    fun volumeCountsCompletedWorkingSetsOnly() {
        val blocks = listOf(
            block(
                bench, 1,
                set(40.0, 10, type = SetType.WARMUP),
                set(80.0, 8),
                set(80.0, 8, completed = false),
                set(70.0, 10, type = SetType.DROP),
            ),
        )
        val summary = WorkoutStats.summarize(blocks, startedAt = 0, now = 3_600_000)
        assertEquals(80.0 * 8 + 70.0 * 10, summary.volumeKg, 0.0)
        assertEquals(3, summary.completedSets)
        assertEquals(1, summary.incompleteSets)
        // The unticked 80 × 8 has numbers in it, so finishing offers to tick it.
        assertEquals(1, summary.filledIncompleteSets)
        assertEquals(1, summary.exerciseCount)
        assertEquals(3_600_000, summary.durationMs)
    }

    @Test
    fun noHistoryMeansNoRecords() {
        val blocks = listOf(block(bench, 1, set(100.0, 5)))
        assertTrue(WorkoutStats.detectRecords(blocks, emptyList()).isEmpty())
    }

    @Test
    fun heavierWeightAndBetterEstimateAreRecords() {
        val history = listOf(PastSet("bench", 95.0, 5), PastSet("bench", 80.0, 12))
        val blocks = listOf(block(bench, 1, set(100.0, 3), set(110.0, 0)))
        val records = WorkoutStats.detectRecords(blocks, history).associateBy { it.kind }

        assertEquals(100.0, records.getValue(PrKind.HEAVIEST_WEIGHT).value, 0.0)
        assertEquals(95.0, records.getValue(PrKind.HEAVIEST_WEIGHT).previous, 0.0)
        // 100 × 3 estimates 110 kg, short of 80 × 12 at 112 kg, so no 1RM record; 110 × 0 doesn't count.
        assertNull(records[PrKind.BEST_ONE_REP_MAX])
    }

    @Test
    fun warmupsAndIncompleteSetsNeverSetRecords() {
        val history = listOf(PastSet("bench", 80.0, 5))
        val blocks = listOf(block(bench, 1, set(120.0, 5, type = SetType.WARMUP), set(120.0, 5, completed = false)))
        assertTrue(WorkoutStats.detectRecords(blocks, history).isEmpty())
    }

    @Test
    fun bodyweightExercisesTrackMostReps() {
        val history = listOf(PastSet("pullup", null, 10))
        val records = WorkoutStats.detectRecords(listOf(block(pullup, 1, set(null, 12))), history)
        assertEquals(listOf(PrKind.MOST_REPS), records.map { it.kind })
        assertEquals(12.0, records.single().value, 0.0)
    }

    @Test
    fun setLabelsNumberNormalSetsOnly() {
        val sets = listOf(set(40.0, 10, type = SetType.WARMUP), set(80.0, 8), set(80.0, 8), set(60.0, 12, type = SetType.DROP))
        assertEquals(listOf("W", "1", "2", "D"), WorkoutStats.setLabels(sets))
    }

    @Test
    fun nextSetLabelLooksAheadAcrossExercises() {
        val first = set(80.0, 8)
        val second = set(80.0, 8, completed = false)
        val pullFirst = set(null, 10, completed = false)
        val blocks = listOf(block(bench, 1, first, second), block(pullup, 2, pullFirst))

        assertEquals("Bench Press · Set 2 · 80 kg × 8", WorkoutStats.nextSetLabel(blocks, first.id))
        val benchDone = listOf(block(bench, 1, first, second.copy(completed = true)), block(pullup, 2, pullFirst))
        assertEquals("Pullups · Set 1 · 10 reps", WorkoutStats.nextSetLabel(benchDone, second.id))
        assertNull(WorkoutStats.nextSetLabel(blocks, pullFirst.id))
    }

    private fun recordSet(workoutId: Long, day: Long, exercise: Exercise, setId: Long, weight: Double?, reps: Int?) = RecordSet(
        workoutId = workoutId,
        startedAt = day * 86_400_000,
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        trackingType = exercise.trackingType.name,
        setId = setId,
        weightKg = weight,
        reps = reps,
    )

    @Test
    fun historyRecordsFollowDateOrderAndSkipTheFirstSession() {
        val records = WorkoutStats.recordsByWorkout(
            listOf(
                // Listed out of order on purpose: workout 3 happened before workout 2.
                recordSet(2, day = 3, bench, setId = 20, weight = 105.0, reps = 3),
                recordSet(1, day = 1, bench, setId = 10, weight = 100.0, reps = 5),
                recordSet(3, day = 2, bench, setId = 30, weight = 100.0, reps = 8),
                recordSet(3, day = 2, pullup, setId = 31, weight = null, reps = 12),
            ),
        )

        // Workout 1 is the first bench session, and workout 3 is the first pull-up session.
        assertNull(records[1])
        val day2 = records.getValue(3)
        assertEquals(listOf(PrKind.BEST_ONE_REP_MAX), day2.map { it.kind })
        assertEquals(30L, day2.single().setId)
        assertEquals(WorkoutStats.estimatedOneRepMax(100.0, 5), day2.single().previous, 0.001)

        // 105 × 3 is heavier than 100, but its estimated 1RM (115.5) is below 100 × 8 (126.7).
        val day3 = records.getValue(2)
        assertEquals(listOf(PrKind.HEAVIEST_WEIGHT), day3.map { it.kind })
        assertEquals(105.0, day3.single().value, 0.0)
        assertEquals(20L, day3.single().setId)
    }

    @Test
    fun historyRecordsCountBodyweightReps() {
        val records = WorkoutStats.recordsByWorkout(
            listOf(
                recordSet(1, day = 1, pullup, setId = 1, weight = null, reps = 10),
                recordSet(2, day = 2, pullup, setId = 2, weight = null, reps = 9),
                recordSet(3, day = 3, pullup, setId = 3, weight = null, reps = 11),
            ),
        )
        assertEquals(setOf(3L), records.keys)
        val record = records.getValue(3).single()
        assertEquals(PrKind.MOST_REPS, record.kind)
        assertEquals(10.0, record.previous, 0.0)
    }
}
