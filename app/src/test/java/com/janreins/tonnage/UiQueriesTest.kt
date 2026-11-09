package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.RecentExercise
import com.janreins.tonnage.data.model.RoutineWorkoutTime
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The read-only queries behind the routine cards, exercise picker, finish screen and history list. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UiQueriesTest {
    private lateinit var db: AppDatabase
    private val bench = Exercise(id = "bench", name = "Bench Press", category = "strength", equipment = "barbell", primaryMuscles = "chest")
    private val squat = Exercise(id = "squat", name = "Squat", category = "strength", equipment = "barbell", primaryMuscles = "quadriceps")
    private val row = Exercise(id = "row", name = "Barbell Row", category = "strength", equipment = "barbell", primaryMuscles = "middle back")

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.exerciseDao().insertAll(listOf(bench, squat, row))
    }

    @After fun tearDown() = db.close()

    /** A workout with one completed 100 kg × 5 set (plus a warm-up) per exercise. */
    private suspend fun workout(startedAt: Long, endedAt: Long?, routineId: Long? = null, vararg exerciseIds: String): Long =
        db.workoutDao().insertWorkoutWithContents(
            Workout(title = "W$startedAt", startedAt = startedAt, endedAt = endedAt, routineId = routineId),
            exerciseIds.map { id ->
                WorkoutExercise(workoutId = 0, exerciseId = id, position = 0, restSeconds = 90) to listOf(
                    WorkoutSet(workoutExerciseId = 0, position = 0, type = SetType.WARMUP, weightKg = 40.0, reps = 10, completed = true),
                    WorkoutSet(workoutExerciseId = 0, position = 1, weightKg = 100.0, reps = 5, completed = true),
                    WorkoutSet(workoutExerciseId = 0, position = 2, weightKg = 100.0, reps = 5, completed = false),
                )
            },
        )

    @Test fun recentExercisesComeFromFinishedWorkoutsNewestFirst() = runTest {
        workout(1_000, 2_000, null, "bench", "squat")
        workout(5_000, 6_000, null, "bench")
        workout(9_000, null, null, "row") // still in progress: not "done"
        assertEquals(
            listOf(RecentExercise("bench", 5_000), RecentExercise("squat", 1_000)),
            db.workoutDao().observeRecentExercises(8).first(),
        )
        assertEquals(1, db.workoutDao().observeRecentExercises(1).first().size)
    }

    @Test fun routineTimesLeaveOutUnfinishedAndRoutinelessWorkouts() = runTest {
        workout(1_000, 4_000, 3, "bench")
        workout(5_000, null, 3, "bench")
        workout(7_000, 8_000, null, "bench")
        assertEquals(listOf(RoutineWorkoutTime(3, 1_000, 4_000)), db.workoutDao().observeRoutineWorkoutTimes().first())
    }

    @Test fun lastRoutineVolumeUsesTheLatestOtherFinishedWorkoutOfThatRoutine() = runTest {
        val repository = WorkoutRepository(db.workoutDao(), db.exerciseDao())
        workout(1_000, 2_000, 3, "bench")
        workout(3_000, 4_000, 3, "bench", "squat")
        workout(5_000, 6_000, 4, "bench")
        val activeId = workout(7_000, null, 3, "bench")
        val active = repository.observeActive().first()!!
        assertEquals(activeId, active.workout.id)
        // Two exercises × one ticked 100 × 5 working set; warm-ups and unticked sets don't count.
        assertEquals(1_000.0, repository.lastRoutineVolume(active)!!, 0.0)
    }

    @Test fun noLastRoutineVolumeWithoutAnEarlierWorkout() = runTest {
        val repository = WorkoutRepository(db.workoutDao(), db.exerciseDao())
        workout(7_000, null, 3, "bench")
        assertNull(repository.lastRoutineVolume(repository.observeActive().first()!!))
        db.workoutDao().deleteWorkout(repository.observeActive().first()!!.workout.id)
        workout(8_000, null, null, "bench")
        assertNull(repository.lastRoutineVolume(repository.observeActive().first()!!))
    }

    @Test fun historyExerciseNamesFollowWorkoutOrder() = runTest {
        val id = workout(1_000, 2_000, null, "squat", "bench", "row")
        workout(3_000, null, null, "bench")
        val names = WorkoutRepository(db.workoutDao(), db.exerciseDao()).observeHistoryExerciseNames().first()
        assertEquals(mapOf(id to listOf("Squat", "Bench Press", "Barbell Row")), names)
    }
}
