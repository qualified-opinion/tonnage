package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.repository.ExerciseRepository
import com.janreins.tonnage.data.repository.WorkoutRepository
import com.janreins.tonnage.domain.PrKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WorkoutRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var workouts: WorkoutRepository
    private var now = 1_700_000_000_000L

    private val bench = Exercise(id = "bench", name = "Bench Press", category = "strength", equipment = "barbell", primaryMuscles = "chest")
    private val squat = Exercise(id = "squat", name = "Squat", category = "strength", equipment = "barbell", primaryMuscles = "quadriceps")

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.exerciseDao().insertAll(listOf(bench, squat))
        workouts = WorkoutRepository(db.workoutDao(), db.exerciseDao(), now = { now })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun active() = workouts.observeActive().first()!!

    @Test
    fun startingTwiceReusesTheActiveWorkout() = runTest {
        val first = workouts.startEmpty()
        assertEquals(first, workouts.startEmpty())
    }

    @Test
    fun addedExercisesGetOneEmptySetAndAddSetCopiesTheLastOne() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench", "squat"), restSeconds = 120)

        var blocks = active().blocks
        assertEquals(listOf("Bench Press", "Squat"), blocks.map { it.name })
        assertEquals(listOf(1, 1), blocks.map { it.sets.size })
        assertEquals(120, blocks[0].workoutExercise.restSeconds)

        val firstSet = blocks[0].sets.single()
        workouts.setWeight(firstSet.id, 80.0)
        workouts.setReps(firstSet.id, 8)
        workouts.addSet(blocks[0].workoutExercise.id)

        blocks = active().blocks
        val copied = blocks[0].sets[1]
        assertEquals(80.0, copied.weightKg!!, 0.0)
        assertEquals(8, copied.reps)
        assertFalse(copied.completed)
        assertEquals(1, copied.position)
    }

    @Test
    fun activeWorkoutIsReadBackFromTheDatabase() = runTest {
        // What a fresh process sees after the app was killed mid-workout.
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), restSeconds = 90)
        val set = active().blocks.single().sets.single()
        workouts.setWeight(set.id, 100.0)
        workouts.setReps(set.id, 5)
        workouts.setCompleted(set.id, true)

        val reopened = WorkoutRepository(db.workoutDao(), db.exerciseDao())
        assertEquals(id, reopened.getActive()!!.id)
        val restored = reopened.observeActive().first()!!.blocks.single().sets.single()
        assertEquals(100.0, restored.weightKg!!, 0.0)
        assertEquals(5, restored.reps)
        assertTrue(restored.completed)
        assertEquals(now, restored.completedAt)
    }

    @Test
    fun finishingKeepsOnlyCompletedSetsAndMovesToHistory() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench", "squat"), restSeconds = 90)
        val benchBlock = active().blocks[0]
        val done = benchBlock.sets.single()
        workouts.setWeight(done.id, 100.0)
        workouts.setReps(done.id, 5)
        workouts.setCompleted(done.id, true)
        workouts.addSet(benchBlock.workoutExercise.id)

        now += 3_600_000
        workouts.finish(id)

        assertNull(workouts.observeActive().first())
        val history = workouts.observeHistory().first()
        assertEquals(1, history.size)
        assertEquals(1, history[0].exerciseCount)
        assertEquals(1, history[0].setCount)
        assertEquals(500.0, history[0].volumeKg, 0.0)
        assertEquals(now, history[0].endedAt)
    }

    @Test
    fun recordsCompareAgainstEarlierFinishedWorkouts() = runTest {
        suspend fun logBench(weight: Double, reps: Int, type: SetType = SetType.NORMAL): Long {
            val id = workouts.startEmpty()
            workouts.addExercises(id, listOf("bench"), restSeconds = 90)
            val set = active().blocks.single().sets.single()
            workouts.setWeight(set.id, weight)
            workouts.setReps(set.id, reps)
            workouts.setType(set.id, type)
            workouts.setCompleted(set.id, true)
            return id
        }

        workouts.finish(logBench(100.0, 5))
        logBench(105.0, 5)
        val records = workouts.recordsFor(active())
        assertEquals(setOf(PrKind.HEAVIEST_WEIGHT, PrKind.BEST_ONE_REP_MAX), records.map { it.kind }.toSet())
        assertEquals(100.0, records.first { it.kind == PrKind.HEAVIEST_WEIGHT }.previous, 0.0)
    }

    @Test
    fun discardingDeletesEverything() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), restSeconds = 90)
        workouts.discard(id)

        assertNull(workouts.getActive())
        assertTrue(db.workoutDao().getWorkoutExercises(id).isEmpty())
        assertTrue(db.workoutDao().observeSetsForWorkout(id).first().isEmpty())
    }

    @Test
    fun customExercisesUsedInAWorkoutAreKept() = runTest {
        val exercises = ExerciseRepository(db.exerciseDao())
        val custom = exercises.addCustom("Landmine Press", "shoulders", "other", TrackingType.WEIGHT_REPS)
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf(custom.id), restSeconds = 90)

        assertFalse(exercises.deleteCustom(custom.id))
        workouts.discard(id)
        assertTrue(exercises.deleteCustom(custom.id))
    }
}
