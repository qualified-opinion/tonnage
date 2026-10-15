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
import com.janreins.tonnage.data.repository.RoutineRepository
import com.janreins.tonnage.data.repository.WorkoutRepository
import com.janreins.tonnage.ui.routines.RoutineDraft
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
class RoutineRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var workouts: WorkoutRepository
    private var key = 0L
    private val nextKey = { ++key }

    private val bench = Exercise(id = "bench", name = "Bench Press", category = "strength", equipment = "barbell", primaryMuscles = "chest")
    private val squat = Exercise(id = "squat", name = "Squat", category = "strength", equipment = "barbell", primaryMuscles = "quadriceps")

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.exerciseDao().insertAll(listOf(bench, squat))
        routines = RoutineRepository(db.routineDao(), db.exerciseDao())
        workouts = WorkoutRepository(db.workoutDao(), db.exerciseDao())
    }

    @After
    fun tearDown() = db.close()

    /** Push A: bench 2 × (warm-up 40 × 10, then 80 × 8), then squat with one empty set. */
    private suspend fun savePushA(): Long {
        var draft = RoutineDraft(name = "Push A").addExercises(listOf("bench", "squat"), restSeconds = 120, nextKey)
        val benchKey = draft.exercises[0].key
        val firstSet = draft.exercises[0].sets.single().key
        draft = draft.updateSet(firstSet) { it.copy(type = SetType.WARMUP, weightKg = 40.0, reps = 10) }
            .addSet(benchKey, nextKey)
        val secondSet = draft.exercises[0].sets[1].key
        draft = draft.updateSet(secondSet) { it.copy(weightKg = 80.0, reps = 8) }
        return routines.save(draft.id, draft.name, draft.toEntities())
    }

    @Test
    fun savedRoutineLoadsBackInOrder() = runTest {
        val id = savePushA()

        val draft = RoutineDraft.from(routines.load(id)!!, nextKey)
        assertEquals("Push A", draft.name)
        assertEquals(listOf("bench", "squat"), draft.exercises.map { it.exerciseId })
        assertEquals(120, draft.exercises[0].restSeconds)
        val sets = draft.exercises[0].sets
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL), sets.map { it.type })
        assertEquals(40.0 to 10, sets[0].weightKg to sets[0].reps)
        assertEquals(80.0 to 8, sets[1].weightKg to sets[1].reps)

        val summary = routines.observeSummaries().first().single()
        assertEquals(listOf("Bench Press", "Squat"), summary.exerciseNames)
    }

    @Test
    fun savingAnEditReplacesTheOldContents() = runTest {
        val id = savePushA()
        val edited = RoutineDraft.from(routines.load(id)!!, nextKey).let { draft ->
            draft.copy(name = "Push B").removeExercise(draft.exercises[0].key)
        }
        assertEquals(id, routines.save(edited.id, edited.name, edited.toEntities()))

        val contents = routines.load(id)!!
        assertEquals("Push B", contents.routine.name)
        assertEquals(listOf("squat"), contents.exercises.map { it.first.exerciseId })
        assertEquals(1, routines.observeSummaries().first().size)
    }

    @Test
    fun duplicateCopiesEverything() = runTest {
        val id = savePushA()
        val copyId = routines.duplicate(id)!!

        val copy = routines.load(copyId)!!
        assertEquals("Push A (copy)", copy.routine.name)
        assertEquals(listOf(2, 1), copy.exercises.map { it.second.size })
        assertEquals(listOf("Push A", "Push A (copy)"), routines.observeSummaries().first().map { it.routine.name })
    }

    @Test
    fun startingARoutineFillsInItsSets() = runTest {
        val id = savePushA()
        workouts.startFromRoutine(routines.load(id)!!)

        val active = workouts.observeActive().first()!!
        assertEquals("Push A", active.workout.title)
        assertEquals(id, active.workout.routineId)
        assertEquals(listOf("Bench Press", "Squat"), active.blocks.map { it.name })
        assertEquals(120, active.blocks[0].workoutExercise.restSeconds)
        val benchSets = active.blocks[0].sets
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL), benchSets.map { it.type })
        assertEquals(80.0, benchSets[1].weightKg!!, 0.0)
        assertEquals(8, benchSets[1].reps)
        assertFalse(benchSets.any { it.completed })
        assertEquals(1, active.blocks[1].sets.size)
    }

    @Test
    fun deletingARoutineKeepsWorkoutsLoggedWithIt() = runTest {
        val id = savePushA()
        val workoutId = workouts.startFromRoutine(routines.load(id)!!)
        val set = workouts.observeActive().first()!!.blocks[0].sets[0]
        workouts.setCompleted(set.id, true)
        workouts.finish(workoutId)

        routines.delete(id)

        assertNull(routines.load(id))
        assertTrue(routines.observeSummaries().first().isEmpty())
        assertEquals(1, workouts.observeHistory().first().size)
    }

    @Test
    fun customExercisesUsedInARoutineAreKept() = runTest {
        val exercises = ExerciseRepository(db.exerciseDao())
        val custom = exercises.addCustom("Landmine Press", "shoulders", "other", TrackingType.WEIGHT_REPS)
        val draft = RoutineDraft(name = "Shoulders").addExercises(listOf(custom.id), restSeconds = 90, nextKey)
        val id = routines.save(draft.id, draft.name, draft.toEntities())

        assertFalse(exercises.deleteCustom(custom.id))
        routines.delete(id)
        assertTrue(exercises.deleteCustom(custom.id))
    }
}
