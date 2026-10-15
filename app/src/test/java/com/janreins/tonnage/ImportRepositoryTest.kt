package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.hevy.HevyCsv
import com.janreins.tonnage.data.hevy.MatchConfidence
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.local.ExerciseSeed
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.repository.ExerciseChoice
import com.janreins.tonnage.data.repository.ImportRepository
import com.janreins.tonnage.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ImportRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var importer: ImportRepository
    private lateinit var workouts: WorkoutRepository
    private val csv = javaClass.getResource("/hevy_sample.csv")!!.readText()

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.exerciseDao().insertAll(
            ExerciseSeed.parse(context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() }),
        )
        importer = ImportRepository(db, now = { 5_000L })
        workouts = WorkoutRepository(db.workoutDao(), db.exerciseDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun previewCountsMatchTheFileAndOnlyUnsureNamesNeedReview() = runTest {
        val plan = importer.prepare(csv)

        assertEquals(3, plan.newWorkouts.size)
        assertEquals(9, plan.setCount)
        assertEquals(0, plan.skipped)
        assertEquals(HevyCsv.parseDate("11 Jul 2026, 08:30"), plan.firstStartedAt)
        assertEquals(HevyCsv.parseDate("22 Aug 2026, 09:03"), plan.lastStartedAt)
        assertEquals(mapOf("Bench Press (Barbell)" to 5, "Kneeling Push Up" to 1, "Face Pull" to 1, "Walking" to 1, "Incline Push Ups" to 1), plan.setCounts)
        assertEquals(setOf("Kneeling Push Up", "Walking"), plan.matches.filter { it.confidence.needsReview }.map { it.hevyName }.toSet())
        // Nothing is saved until the import runs.
        assertTrue(workouts.observeHistory().first().isEmpty())
    }

    @Test
    fun importAddsFinishedWorkoutsAndCreatesUnmatchedExercises() = runTest {
        val plan = importer.prepare(csv)
        val result = importer.import(plan, plan.defaultChoices(), restSeconds = 120)

        assertEquals(3, result.workouts)
        assertEquals(9, result.sets)
        assertEquals(2, result.createdExercises)

        val custom = db.importDao().customExercises().associateBy { it.name }
        assertEquals(TrackingType.DISTANCE_DURATION, custom.getValue("Walking").trackingType)
        assertEquals(TrackingType.BODYWEIGHT_REPS, custom.getValue("Kneeling Push Up").trackingType)

        val history = workouts.observeHistory().first()
        assertEquals(listOf("Push, heavy", "Afternoon workout 💪", "Morning workout ☀️"), history.map { it.title })
        assertEquals(9, history.sumOf { it.setCount })

        val push = workouts.observeWorkout(history.first().id).first()!!
        assertEquals(Workout.SOURCE_HEVY, push.workout.source)
        assertEquals("Felt strong", push.workout.notes)
        val bench = push.blocks.first()
        assertEquals("Barbell_Bench_Press_-_Medium_Grip", bench.workoutExercise.exerciseId)
        assertEquals("Pause reps", bench.workoutExercise.notes)
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL, SetType.FAILURE), bench.sets.map { it.type })
        assertEquals(listOf(40.0, 60.0, 60.0), bench.sets.map { it.weightKg })
        assertEquals(8.5, bench.sets[1].rpe!!, 0.0)
        assertTrue(bench.sets.all { it.completed && it.completedAt == push.workout.endedAt })
    }

    @Test
    fun importingTheSameFileAgainAddsNothing() = runTest {
        val first = importer.prepare(csv)
        importer.import(first, first.defaultChoices(), restSeconds = 90)

        val again = importer.prepare(csv)
        assertTrue(again.newWorkouts.isEmpty())
        assertEquals(3, again.skipped)
        assertEquals(0, again.setCount)

        val result = importer.import(again, again.defaultChoices(), restSeconds = 90)
        assertEquals(0, result.workouts)
        assertEquals(3, workouts.observeHistory().first().size)
    }

    @Test
    fun choicesAreRememberedForTheNextExport() = runTest {
        val first = importer.prepare(csv)
        val choices = first.defaultChoices() + ("Kneeling Push Up" to ExerciseChoice.Existing("Pushups"))
        val created = importer.import(first, choices, restSeconds = 90)
        assertEquals(1, created.createdExercises)
        val walkingId = db.importDao().customExercises().single { it.name == "Walking" }.id

        // A later export: the same exercises plus a workout done since.
        val later = csv + "\n" + """
            "Legs","29 Aug 2026, 09:00","29 Aug 2026, 10:00","","Kneeling Push Up",,"",0,"normal",,12,,,
            "Legs","29 Aug 2026, 09:00","29 Aug 2026, 10:00","","Walking",,"",0,"normal",,,2,900,
        """.trimIndent()
        val plan = importer.prepare(later)

        assertEquals(1, plan.newWorkouts.size)
        assertEquals(3, plan.skipped)
        assertFalse(plan.matches.any { it.confidence.needsReview })
        assertTrue(plan.matches.all { it.confidence == MatchConfidence.SAVED })
        assertEquals(mapOf("Kneeling Push Up" to "Pushups", "Walking" to walkingId), plan.matches.associate { it.hevyName to it.exerciseId })

        val result = importer.import(plan, plan.defaultChoices(), restSeconds = 90)
        assertEquals(0, result.createdExercises)
        assertEquals(4, workouts.observeHistory().first().size)
    }
}
