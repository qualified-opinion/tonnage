package com.janreins.tonnage

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.repository.WorkoutRepository
import com.janreins.tonnage.ui.history.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var model: HistoryViewModel
    private val store = ViewModelStore()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        workouts = WorkoutRepository(db.workoutDao(), db.exerciseDao())
        model = HistoryViewModel(app, db)
        store.put("history", model)
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun startWorkout(): Long {
        db.exerciseDao().insertAll(listOf("bench", "squat").map {
            Exercise(id = it, name = it, category = "strength", equipment = "barbell", primaryMuscles = "chest")
        })
        return workouts.startEmpty().also { workouts.addExercises(it, listOf("bench"), 90) }
    }

    private suspend fun openAndWait(id: Long) {
        val jobs = model.viewModelScope.coroutineContext[Job]!!.children.toSet()
        model.open(id)
        assertNull(model.openId.value) // Cleanup finishes before the details become editable.
        model.viewModelScope.coroutineContext[Job]!!.children.single { it !in jobs }.join()
        assertEquals(id, model.openId.value)
    }

    @Test fun openingFinishedWorkoutRecoversBlankRowsFromAnInterruptedEdit() = runTest {
        val id = startWorkout()
        val original = workouts.observeActive().first()!!.blocks.single().sets.single()
        workouts.setWeight(original.id, 60.0)
        workouts.setReps(original.id, 8)
        workouts.setCompleted(original.id, true)
        workouts.finish(id)
        val saved = db.workoutDao().getSet(original.id)
        workouts.addSet(original.workoutExerciseId, completed = true)
        val blank = db.workoutDao().getSets(original.workoutExerciseId).last()
        workouts.setWeight(blank.id, null)
        workouts.setReps(blank.id, null)
        workouts.addExercises(id, listOf("squat"), 90, completed = true)
        // These rows were committed before the previous process was killed without tapping Done.
        assertEquals(3, db.workoutDao().observeSetsForWorkout(id).first().size)
        openAndWait(id)
        assertEquals(listOf(saved), db.workoutDao().observeSetsForWorkout(id).first())
        assertEquals(listOf("bench"), db.workoutDao().getWorkoutExercises(id).map { it.exerciseId })
        assertEquals(1, workouts.observeHistory().first().single().setCount)
    }

    @Test fun openingAnActiveWorkoutDoesNotTidyItsBlankSets() = runTest {
        val id = startWorkout()
        val before = db.workoutDao().observeSetsForWorkout(id).first()
        openAndWait(id)
        assertEquals(before, db.workoutDao().observeSetsForWorkout(id).first())
        assertNull(db.workoutDao().getWorkout(id)!!.endedAt)
    }
    @Test fun closingWhileOpenIsPendingDoesNotReopenTheWorkout() = runTest {
        val id = startWorkout()
        model.open(id)
        model.close()
        dispatcher.scheduler.runCurrent()
        assertNull(model.openId.value)
    }

}
