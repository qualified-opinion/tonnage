package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.hevy.HevyCsv
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.local.ExerciseSeed
import com.janreins.tonnage.data.repository.ImportRepository
import com.janreins.tonnage.data.repository.WorkoutRepository
import com.janreins.tonnage.domain.MilestoneKind
import com.janreins.tonnage.domain.Milestones
import com.janreins.tonnage.domain.MuscleSets
import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.domain.ProgressMetric
import com.janreins.tonnage.domain.WorkoutStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Progress tab's numbers over a Hevy import in the format of Jan's export. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProgressHistoryTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.exerciseDao().insertAll(
            ExerciseSeed.parse(context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() }),
        )
        val importer = ImportRepository(db, com.janreins.tonnage.data.settings.SettingsStore(context))
        val plan = importer.prepare(javaClass.getResource("/hevy_sample.csv")!!.readText())
        importer.import(plan, plan.defaultChoices(), restSeconds = 90)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun onlyWorkingSetsOfFinishedWorkoutsCount() = runTest {
        // A workout in progress with a ticked set must not show on the Progress tab yet.
        val workouts = WorkoutRepository(db.workoutDao(), db.exerciseDao())
        val active = workouts.startEmpty()
        workouts.addExercises(active, listOf("Barbell_Squat"), restSeconds = 90)
        val set = workouts.observeActive().first()!!.blocks.single().sets.single()
        workouts.setWeight(set.id, 100.0)
        workouts.setReps(set.id, 5)
        workouts.setCompleted(set.id, true)

        val sets = db.workoutDao().observeProgressSets().first()
        // 9 sets in the file, one of them a warm-up.
        assertEquals(8, sets.size)
        assertTrue(sets.none { it.exerciseId == "Barbell_Squat" })
    }

    @Test
    fun benchChartHasOnePointPerWorkout() = runTest {
        val sets = db.workoutDao().observeProgressSets().first()
        val bench = Progress.sessions(sets, "Barbell_Bench_Press_-_Medium_Grip")

        assertEquals(listOf(HevyCsv.parseDate("11 Jul 2026, 08:30"), HevyCsv.parseDate("22 Aug 2026, 09:03")), bench.map { it.startedAt })
        assertEquals(50.0 * (1 + 5 / 30.0), bench[0].values.getValue(ProgressMetric.ONE_REP_MAX), 0.001)
        // The warm-up (40 × 10) is left out; 60 × 8 is the best set.
        assertEquals(76.0, bench[1].values.getValue(ProgressMetric.ONE_REP_MAX), 0.001)
        assertEquals("60 kg × 8", bench[1].bestSet)
        assertEquals(60.0 * 8 + 60.0 * 6, bench[1].values.getValue(ProgressMetric.VOLUME), 0.0)

        val walking = Progress.exercises(sets).single { it.name == "Walking" }
        assertEquals(7.0, walking.best, 0.0)
    }

    @Test
    fun weeklyMusclesAndMilestonesFollowTheImport() = runTest {
        val sets = db.workoutDao().observeProgressSets().first()
        val pushWeek = Progress.weekStart(HevyCsv.parseDate("22 Aug 2026, 09:03")!!)
        assertEquals(
            listOf(MuscleSets("chest", 2), MuscleSets("other", 1), MuscleSets("shoulders", 1)),
            Progress.muscles(sets, pushWeek),
        )

        val starts = db.workoutDao().observeFinishedSummaries().first().map { WorkoutStart(it.id, it.startedAt) }
        val report = Milestones.report(starts, sets, now = HevyCsv.parseDate("23 Aug 2026, 10:00")!!)
        val first = report.achieved.single { it.kind == MilestoneKind.WORKOUTS }
        assertEquals("First workout", first.title)
        assertEquals(setOf("Bench press 40 kg", "Bench press 60 kg"), report.achieved.filter { it.kind == MilestoneKind.LIFT }.map { it.title }.toSet())
        assertEquals(HevyCsv.parseDate("11 Jul 2026, 08:30"), first.achievedAt)
        assertEquals("Bench press 80 kg", report.next.single { it.kind == MilestoneKind.LIFT }.title)
    }
}
