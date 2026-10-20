package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.Routine
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.repository.ExerciseRepository
import com.janreins.tonnage.data.repository.WorkoutRepository
import com.janreins.tonnage.domain.WorkoutStats
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
        val exercises = ExerciseRepository(db)
        val custom = exercises.addCustom("Landmine Press", "shoulders", "other", TrackingType.WEIGHT_REPS)
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf(custom.id), restSeconds = 90)

        assertFalse(exercises.deleteCustom(custom.id))
        workouts.discard(id)
        assertTrue(exercises.deleteCustom(custom.id))
    }

    /** Logs and finishes one workout with the given bench sets (weight, reps, ticked). */
    private suspend fun finishBench(routineId: Long?, vararg sets: Triple<Double, Int, Boolean>): Long {
        val id = if (routineId == null) {
            workouts.startEmpty()
        } else {
            db.workoutDao().insertWorkout(Workout(title = "Routine", startedAt = now, routineId = routineId))
        }
        workouts.addExercises(id, listOf("bench"), restSeconds = 90)
        val block = active().blocks.single()
        repeat(sets.size - 1) { workouts.addSet(block.workoutExercise.id) }
        active().blocks.single().sets.zip(sets).forEach { (set, values) ->
            workouts.setWeight(set.id, values.first)
            workouts.setReps(set.id, values.second)
            workouts.setCompleted(set.id, values.third)
        }
        workouts.finish(id)
        now += 86_400_000
        return id
    }

    @Test
    fun previousValuesAreTheLatestCompletedSets() = runTest {
        finishBench(null, Triple(60.0, 10, true))
        finishBench(null, Triple(80.0, 8, true), Triple(85.0, 6, false), Triple(82.5, 7, true))
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench", "squat"), restSeconds = 90)

        val previous = workouts.previousSets(listOf("bench", "squat"), routineId = null)
        // Unticked sets were removed on finish, so last time was 80 × 8 then 82.5 × 7.
        assertEquals(listOf(80.0 to 8, 82.5 to 7), previous.getValue("bench").map { it.weightKg to it.reps })
        assertTrue(previous.getValue("squat").isEmpty())
    }

    @Test
    fun previousValuesPreferTheSameRoutine() = runTest {
        finishBench(routineId = 7, Triple(100.0, 5, true))
        finishBench(null, Triple(60.0, 12, true))

        assertEquals(listOf(100.0 to 5), workouts.previousSets(listOf("bench"), routineId = 7).getValue("bench").map { it.weightKg to it.reps })
        // A routine never done before falls back to the latest time anywhere.
        assertEquals(listOf(60.0 to 12), workouts.previousSets(listOf("bench"), routineId = 8).getValue("bench").map { it.weightKg to it.reps })
        assertEquals(listOf(60.0 to 12), workouts.previousSets(listOf("bench"), routineId = null).getValue("bench").map { it.weightKg to it.reps })
    }

    @Test
    fun theActiveWorkoutIsNotItsOwnPrevious() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), restSeconds = 90)
        val set = active().blocks.single().sets.single()
        workouts.setWeight(set.id, 70.0)
        workouts.setReps(set.id, 10)
        workouts.setCompleted(set.id, true)

        assertTrue(workouts.previousSets(listOf("bench"), routineId = null).getValue("bench").isEmpty())
    }

    @Test
    fun usingAPreviousValueCopiesItsNumbers() = runTest {
        finishBench(null, Triple(80.0, 8, true))
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), restSeconds = 90)
        val previous = workouts.previousSets(listOf("bench"), routineId = null).getValue("bench").single()

        workouts.copyValues(active().blocks.single().sets.single().id, previous)

        val set = active().blocks.single().sets.single()
        assertEquals(80.0, set.weightKg!!, 0.0)
        assertEquals(8, set.reps)
        assertFalse(set.completed)
    }

    @Test
    fun tickingABlankSetFillsItFromLastTime() = runTest {
        finishBench(null, Triple(80.0, 8, true))
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), restSeconds = 90)
        val previous = workouts.previousSets(listOf("bench"), routineId = null).getValue("bench")
        val set = active().blocks.single().sets.single()

        assertTrue(workouts.complete(set.id, TrackingType.WEIGHT_REPS, previous.getOrNull(0)))

        val ticked = active().blocks.single().sets.single()
        assertTrue(ticked.completed)
        assertEquals(80.0, ticked.weightKg!!, 0.0)
        assertEquals(8, ticked.reps)
    }

    @Test
    fun tickingABlankSetWithNoPreviousIsIgnored() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), restSeconds = 90)
        val set = active().blocks.single().sets.single()

        assertFalse(workouts.complete(set.id, TrackingType.WEIGHT_REPS, previous = null))
        assertFalse(active().blocks.single().sets.single().completed)
    }

    @Test
    fun tickingAFilledSetKeepsWhatWasTyped() = runTest {
        finishBench(null, Triple(80.0, 8, true))
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), restSeconds = 90)
        val previous = workouts.previousSets(listOf("bench"), routineId = null).getValue("bench").single()
        val set = active().blocks.single().sets.single()
        workouts.setWeight(set.id, 85.0)

        assertTrue(workouts.complete(set.id, TrackingType.WEIGHT_REPS, previous))

        val ticked = active().blocks.single().sets.single()
        assertEquals(85.0, ticked.weightKg!!, 0.0)
        assertNull(ticked.reps)
    }

    @Test
    fun finishingDropsBlankTickedSets() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), restSeconds = 90)
        val block = active().blocks.single()
        val filled = block.sets.single()
        workouts.setWeight(filled.id, 60.0)
        workouts.setReps(filled.id, 10)
        workouts.setCompleted(filled.id, true)
        // A blank set ticked directly, as older versions allowed.
        workouts.addSet(block.workoutExercise.id)
        val blank = active().blocks.single().sets[1]
        workouts.setWeight(blank.id, null)
        workouts.setReps(blank.id, null)
        workouts.setCompleted(blank.id, true)

        workouts.finish(id)

        assertEquals(1, workouts.observeHistory().first().single().setCount)
    }

    @Test
    fun tickingFilledSetsTicksExactlyTheFilledOnes() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench", "squat"), restSeconds = 90)
        var blocks = active().blocks
        val benchSet = blocks[0].sets.single()
        workouts.setWeight(benchSet.id, 70.0)
        workouts.setReps(benchSet.id, 8)
        workouts.addSet(blocks[0].workoutExercise.id)
        val squatSet = blocks[1].sets.single()
        workouts.setReps(squatSet.id, 5)
        workouts.addSet(blocks[1].workoutExercise.id)
        val emptySquat = active().blocks[1].sets[1]
        workouts.setReps(emptySquat.id, null)

        assertEquals(3, workouts.completeFilled(active().blocks))

        blocks = active().blocks
        assertEquals(listOf(true, true), blocks[0].sets.map { it.completed })
        assertEquals(listOf(true, false), blocks[1].sets.map { it.completed })
    }

    @Test
    fun exercisesCanBeMovedUpAndDown() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench", "squat"), restSeconds = 90)
        val squatBlock = active().blocks[1]

        workouts.moveExercise(id, squatBlock.workoutExercise.id, -1)
        assertEquals(listOf("Squat", "Bench Press"), active().blocks.map { it.name })
        workouts.moveExercise(id, squatBlock.workoutExercise.id, -1)
        assertEquals(listOf("Squat", "Bench Press"), active().blocks.map { it.name })
        workouts.moveExercise(id, squatBlock.workoutExercise.id, 1)
        assertEquals(listOf("Bench Press", "Squat"), active().blocks.map { it.name })
    }

    @Test
    fun editingAnOldWorkoutRecalculatesLaterRecords() = runTest {
        val first = finishBench(null, Triple(100.0, 5, true))
        val second = finishBench(null, Triple(110.0, 5, true))
        assertEquals(setOf(second), workouts.observeRecords().first().keys)

        // Correct a typo in the first workout: it was really 120 kg, so the second set no record.
        val firstSet = workouts.observeWorkout(first).first()!!.blocks.single().sets.single()
        workouts.setWeight(firstSet.id, 120.0)
        assertTrue(workouts.observeRecords().first().isEmpty())

        workouts.discard(first)
        assertTrue(workouts.observeRecords().first().isEmpty())
        assertEquals(listOf(second), workouts.observeHistory().first().map { it.id })
    }

    @Test
    fun setsAddedWhileEditingCountAndBlankOnesAreTidiedAway() = runTest {
        val id = finishBench(null, Triple(100.0, 5, true))
        val block = workouts.observeWorkout(id).first()!!.blocks.single()

        workouts.addSet(block.workoutExercise.id, completed = true)
        workouts.addExercises(id, listOf("squat"), restSeconds = 90, completed = true)
        var edited = workouts.observeWorkout(id).first()!!
        assertTrue(edited.blocks.all { b -> b.sets.all { it.completed } })
        assertEquals(1000.0, workouts.observeHistory().first().single().volumeKg, 0.0)

        // The squat was added but never filled in, so Done removes it.
        workouts.tidyEdited(id)
        edited = workouts.observeWorkout(id).first()!!
        assertEquals(listOf("Bench Press"), edited.blocks.map { it.name })
        assertEquals(2, edited.blocks.single().sets.size)
    }
    private suspend fun startPlannedWorkout(): Long {
        val routineId = db.routineDao().save(Routine(name = "Plan"), listOf("squat", "bench").mapIndexed { index, id ->
            RoutineExercise(routineId = 0, exerciseId = id, position = index, restSeconds = 90) to
                (0..2).map { RoutineSet(routineExerciseId = 0, position = it, weightKg = 60.0, reps = 5) }
        })
        return workouts.startFromRoutine(com.janreins.tonnage.data.model.RoutineContents(
            db.routineDao().getRoutine(routineId)!!,
            db.routineDao().getExercises(routineId).map { exercise ->
                exercise to db.routineDao().getSets(routineId).filter { it.routineExerciseId == exercise.id }
            },
        ))
    }

    @Test
    fun untouchedPlanDoesNotNeedTheEnteredSetDiscardWarningEvenAfterReopen() = runTest {
        startPlannedWorkout()
        val reopened = WorkoutRepository(db.workoutDao(), db.exerciseDao()).observeActive().first()!!
        val summary = WorkoutStats.summarize(reopened.blocks, 0, now)
        assertEquals(0, summary.completedSets)
        assertEquals(0, summary.filledIncompleteSets)
        assertEquals(6, summary.plannedIncompleteSets)
        assertTrue(reopened.blocks.none { it.hasLoggedSets })
    }

    @Test
    fun finishingPartOfARoutineSavesOnlyTickedSets() = runTest {
        val id = startPlannedWorkout()
        val squat = active().blocks[0]
        squat.sets.forEach { assertTrue(workouts.complete(it.id, squat.trackingType, null)) }
        // Moving exercises must not make an untouched target look entered.
        workouts.moveExercise(id, squat.workoutExercise.id, 1)
        val summary = WorkoutStats.summarize(active().blocks, 0, now)
        assertEquals(3, summary.completedSets)
        assertEquals(0, summary.filledIncompleteSets)
        workouts.finish(id)
        assertEquals(3, workouts.observeHistory().first().single().setCount)
        assertEquals(listOf("Squat"), workouts.observeWorkout(id).first()!!.blocks.map { it.name })
    }

    @Test
    fun secondarySaveTicksEnteredValuesButNeverUntouchedTargetsOrCopies() = runTest {
        val id = startPlannedWorkout()
        val squat = active().blocks[0]
        workouts.setWeight(squat.sets[0].id, 65.0)
        workouts.addSet(squat.workoutExercise.id)
        val summary = WorkoutStats.summarize(active().blocks, 0, now)
        assertEquals(1, summary.filledIncompleteSets)
        assertEquals(6, summary.plannedIncompleteSets)
        assertEquals(1, workouts.completeFilled(active().blocks, enteredOnly = true))
        workouts.finish(id)
        assertEquals(325.0, workouts.observeHistory().first().single().volumeKg, 0.0)
        assertEquals(1, workouts.observeHistory().first().single().setCount)
    }

    @Test
    fun emptyWorkoutStillOffersEnteredValuesButNotItsAddedCopy() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), 90)
        val block = active().blocks.single()
        workouts.setWeight(block.sets.single().id, 60.0)
        workouts.setReps(block.sets.single().id, 8)
        workouts.addSet(block.workoutExercise.id)
        val summary = WorkoutStats.summarize(active().blocks, 0, now)
        assertEquals(1, summary.filledIncompleteSets)
        assertEquals(1, summary.plannedIncompleteSets)
        assertTrue(active().blocks.single().hasLoggedSets)
        assertEquals(1, workouts.completeFilled(active().blocks, enteredOnly = true))
        workouts.finish(id)
        assertEquals(1, workouts.observeHistory().first().single().setCount)
    }

    @Test
    fun tickedThenClearedSetsLeaveNoWorkoutInHistoryOrProgress() = runTest {
        val id = workouts.startEmpty()
        workouts.addExercises(id, listOf("bench"), 90)
        val set = active().blocks.single().sets.single()
        workouts.setWeight(set.id, 60.0)
        workouts.setReps(set.id, 8)
        workouts.complete(set.id, TrackingType.WEIGHT_REPS, null)
        workouts.setWeight(set.id, null)
        workouts.setReps(set.id, null)
        val summary = WorkoutStats.summarize(active().blocks, 0, now)
        assertEquals(0, summary.completedSets)
        assertEquals(0, summary.exerciseCount)
        workouts.finish(id)
        assertNull(db.workoutDao().getWorkout(id))
        assertTrue(workouts.observeHistory().first().isEmpty())
        assertTrue(db.workoutDao().observeProgressSets().first().isEmpty())
    }

}
