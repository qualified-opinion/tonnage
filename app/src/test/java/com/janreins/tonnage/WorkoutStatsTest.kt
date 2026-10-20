package com.janreins.tonnage

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.PastSet
import com.janreins.tonnage.data.model.RecordSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.repository.recordsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
    @Test
    fun repeatedExerciseBlocksProduceOneRecordPerKind() {
        val blocks = listOf(
            block(bench, 1, set(105.0, 3)),
            block(bench, 2, set(102.5, 10), set(200.0, 10, type = SetType.WARMUP), set(150.0, 10, completed = false)),
        )
        val records = WorkoutStats.detectRecords(blocks, listOf(PastSet("bench", 100.0, 5)))
        assertEquals(2, records.size)
        assertEquals(105.0, records.single { it.kind == PrKind.HEAVIEST_WEIGHT }.value, 0.0)
        assertEquals(136.6666667, records.single { it.kind == PrKind.BEST_ONE_REP_MAX }.value, 0.001)
    }

    @Test
    fun missingOrZeroWeightDoesNotEstablishAWeightedRecordBaseline() {
        listOf<Double?>(null, 0.0).forEach { weight ->
            val prior = recordSet(1, 1, bench, 10, weight, 10)
            val firstWeighted = recordSet(2, 2, bench, 20, 60.0, 5)
            assertTrue(WorkoutStats.recordsByWorkout(listOf(prior, firstWeighted)).isEmpty())
            assertTrue(WorkoutStats.detectRecords(listOf(block(bench, 1, set(60.0, 5))), listOf(PastSet("bench", weight, 10))).isEmpty())
            val later = recordSet(3, 3, bench, 30, 65.0, 6)
            assertEquals(setOf(PrKind.HEAVIEST_WEIGHT, PrKind.BEST_ONE_REP_MAX),
                WorkoutStats.recordsByWorkout(listOf(prior, firstWeighted, later)).getValue(3).map { it.kind }.toSet())
        }
    }

    @Test
    fun bodyweightRecordsAlsoNeedAnEarlierPositiveValue() {
        val zero = recordSet(1, 1, pullup, 10, null, 0)
        val first = recordSet(2, 2, pullup, 20, null, 10)
        assertTrue(WorkoutStats.recordsByWorkout(listOf(zero, first)).isEmpty())
        assertTrue(WorkoutStats.detectRecords(listOf(block(pullup, 1, set(null, 10))), listOf(PastSet("pullup", null, 0))).isEmpty())
        val later = recordSet(3, 3, pullup, 30, null, 11)
        assertEquals(10.0, WorkoutStats.recordsByWorkout(listOf(zero, first, later)).getValue(3).single().previous, 0.0)
    }

    @Test
    fun sixWorkoutHistoryKeepsItsEstablishedRecordsAndMatchesFinishPreviews() {
        val squat = bench.copy(id = "squat", name = "Squat")
        val exercises = listOf(bench, squat, pullup)
        val sessions = listOf(
            listOf(60.0 to 8, 80.0 to 5, null to 6),
            listOf(62.5 to 8, 80.0 to 6, null to 7),
            listOf(62.5 to 6, 82.5 to 4, null to 7),
            listOf(65.0 to 5, 82.5 to 6, null to 8),
            listOf(60.0 to 10, 80.0 to 8, null to 6),
            listOf(67.5 to 6, 85.0 to 5, null to 9),
        )
        val rows = sessions.flatMapIndexed { index, values -> values.mapIndexed { e, (kg, reps) ->
            recordSet(index + 1L, index + 1L, exercises[e], (index + 1L) * 10 + e, kg, reps)
        } }
        val heaviest = PrKind.HEAVIEST_WEIGHT
        val estimate = PrKind.BEST_ONE_REP_MAX
        val reps = PrKind.MOST_REPS
        // Fixed expected badges for ordinary positive history, also valid before this fix.
        val expected = listOf(
            emptyList(),
            listOf("bench" to heaviest, "bench" to estimate, "squat" to estimate, "pullup" to reps),
            listOf("squat" to heaviest),
            listOf("bench" to heaviest, "squat" to estimate, "pullup" to reps),
            listOf("bench" to estimate, "squat" to estimate),
            listOf("bench" to heaviest, "bench" to estimate, "squat" to heaviest, "pullup" to reps),
        )
        val expectedValues = listOf(
            emptyList(),
            listOf(62.5 to 60.0, 79.1666667 to 76.0, 96.0 to 93.3333333, 7.0 to 6.0),
            listOf(82.5 to 80.0),
            listOf(65.0 to 62.5, 99.0 to 96.0, 8.0 to 7.0),
            listOf(80.0 to 79.1666667, 101.3333333 to 99.0),
            listOf(67.5 to 65.0, 81.0 to 80.0, 85.0 to 82.5, 9.0 to 8.0),
        )
        val history = runBlocking { recordsFlow(flowOf(rows.chunked(3).reversed().flatten())).first() }
        sessions.forEachIndexed { index, values ->
            val id = index + 1L
            val records = history[id].orEmpty()
            assertEquals("Workout $id", expected[index], records.map { it.exerciseId to it.kind })
            records.zip(expectedValues[index]).forEach { (record, expectedValue) ->
                assertEquals(expectedValue.first, record.value, 0.0001)
                assertEquals(expectedValue.second, record.previous, 0.0001)
                assertEquals(id * 10 + exercises.indexOfFirst { it.id == record.exerciseId }, record.setId)
            }
            val blocks = values.mapIndexed { e, (kg, count) -> block(exercises[e], e + 1L, set(kg, count)) }
            val past = rows.filter { it.workoutId < id }.map { PastSet(it.exerciseId, it.weightKg, it.reps) }
            assertEquals(records.map { it.copy(setId = 0) }, WorkoutStats.detectRecords(blocks, past))
            assertTrue(records.all { it.previous > 0 && it.value > it.previous })
        }
    }

}
