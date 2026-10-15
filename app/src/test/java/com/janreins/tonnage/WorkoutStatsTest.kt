package com.janreins.tonnage

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.PastSet
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
}
