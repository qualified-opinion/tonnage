package com.janreins.tonnage

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.MilestoneKind
import com.janreins.tonnage.ui.progress.ProgressViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private val store = ViewModelStore()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun workout(at: String) {
        db.exerciseDao().insertAll(listOf(Exercise(id = "bench", name = "Bench", category = "strength", equipment = "barbell", primaryMuscles = "chest")))
        val time = Instant.parse(at).toEpochMilli()
        db.workoutDao().insertWorkoutWithContents(
            Workout(title = "Bench", startedAt = time, endedAt = time + 1000),
            listOf(WorkoutExercise(workoutId = 0, exerciseId = "bench", position = 0, restSeconds = 90) to
                listOf(WorkoutSet(workoutExerciseId = 0, position = 0, weightKg = 60.0, reps = 5, completed = true))),
        )
    }

    @Test fun visibleOverviewRollsToMondayWithoutADatabaseWriteOrResubscription() = runTest {
        workout("2026-10-18T10:00:00Z")
        val base = Instant.parse("2026-10-18T23:59:00Z").toEpochMilli()
        val model = ProgressViewModel(app, db, { base + testScheduler.currentTime }, { ZoneId.of("UTC") }, dispatcher)
        store.put("progress", model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.overview.collect {} }
        val before = model.overview.filterNotNull().first()
        assertEquals(1, before.week.workouts)
        assertEquals(1, before.week.sets)
        assertEquals(300.0, before.week.volumeKg, 0.0)
        assertEquals(1, before.muscles.single().sets)
        advanceTimeBy(60_000); runCurrent()
        val after = model.overview.filterNotNull().first { it.week.weekStart == LocalDate.parse("2026-10-19") }
        assertEquals(0, after.week.workouts)
        assertEquals(0, after.week.sets)
        assertEquals(0.0, after.week.volumeKg, 0.0)
        assertEquals(emptyList<Any>(), after.muscles)
        assertEquals(1, after.weeks[after.weeks.lastIndex - 1].workouts)
        assertEquals(1, after.streak.current) // Last week's run still counts.
    }

    @Test fun resumeRereadsWallClockAndZoneIncludingStreakMilestonesAndMuscles() = runTest {
        workout("2026-10-25T16:30:00Z") // Sunday UTC, Monday in Manila.
        var now = Instant.parse("2026-10-25T17:00:00Z").toEpochMilli()
        var zone = ZoneId.of("UTC")
        val model = ProgressViewModel(app, db, { now }, { zone }, dispatcher)
        store.put("progress", model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.overview.collect {} }
        assertEquals(LocalDate.parse("2026-10-19"), model.overview.filterNotNull().first().week.weekStart)
        zone = ZoneId.of("Asia/Manila")
        model.refresh()
        val changed = model.overview.filterNotNull().first { it.week.weekStart == LocalDate.parse("2026-10-26") }
        assertEquals(1, changed.week.workouts)
        assertEquals(1, changed.muscles.single().sets)
        assertEquals(1, changed.streak.current)
        now = Instant.parse("2026-11-09T01:00:00Z").toEpochMilli()
        model.refresh()
        val later = model.overview.filterNotNull().first { it.week.weekStart == LocalDate.parse("2026-11-09") }
        assertEquals(0, later.week.workouts)
        assertEquals(0, later.streak.current)
        assertEquals("0", later.milestones.next.single { it.kind == MilestoneKind.STREAK }.current)
    }
}
