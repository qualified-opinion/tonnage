package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.backup.BackupJson
import com.janreins.tonnage.data.backup.BackupRepository
import com.janreins.tonnage.data.hevy.HevyFormatException
import com.janreins.tonnage.data.repository.ExerciseRepository
import com.janreins.tonnage.data.hevy.HevyCsv
import com.janreins.tonnage.data.hevy.MatchConfidence
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.local.ExerciseSeed
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.repository.ExerciseChoice
import com.janreins.tonnage.data.settings.SettingsStore
import com.janreins.tonnage.data.repository.ImportRepository
import com.janreins.tonnage.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.util.TimeZone
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
    private lateinit var settings: SettingsStore
    private val csv = javaClass.getResource("/hevy_sample.csv")!!.readText()

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.exerciseDao().insertAll(
            ExerciseSeed.parse(context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() }),
        )
        settings = SettingsStore(context)
        importer = ImportRepository(db, settings, now = { 5_000L })
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
    private fun one(title: String = "A", date: String = "2026-08-01 10:00", weight: String = "", reps: String = "", duration: String = "60") =
        "title,start_time,exercise_title,weight_kg,reps,duration_seconds\n$title,$date,Plank,$weight,$reps,$duration"

    private suspend fun importCsv(text: String): Int {
        val plan = importer.prepare(text)
        return importer.import(plan, plan.defaultChoices(), 90).workouts
    }

    @Test
    fun sameMinuteWorkoutsImportAcrossFilesInEitherOrder() = runTest {
        for (titles in listOf(listOf("A", "B"), listOf("B", "A"))) {
            db.importDao().deleteAllWorkouts()
            titles.forEach { assertEquals(1, importCsv(one(it))) }
            titles.forEach { assertEquals(0, importCsv(one(it))) }
            assertEquals(titles.toSet(), db.importDao().finishedWorkouts().map { it.title }.toSet())
        }
    }

    @Test
    fun groupingAndReimportUseNormalisedTitlesAndDates() = runTest {
        val text = one("  ") + "\n" + one("Workout", "2026-08-01 10:00:00").substringAfter('\n') +
            "\n" + one("B").substringAfter('\n')
        val plan = importer.prepare(text)
        assertEquals(listOf("Workout", "B"), plan.newWorkouts.map { it.title })
        assertEquals(2, importer.import(plan, plan.defaultChoices(), 90).workouts)
        assertEquals(0, importCsv(one("")))
        assertEquals(0, importCsv(one("Workout")))
        assertEquals(0, importCsv(text))
    }

    @Test
    fun transactionRechecksIdentityAndDeduplicatesIncomingPlans() = runTest {
        val a = importer.prepare(one("A"))
        val b = importer.prepare(one("B"))
        assertEquals(1, importer.import(a, a.defaultChoices(), 90).workouts)
        assertEquals(1, importer.import(b.copy(newWorkouts = b.newWorkouts + b.newWorkouts), b.defaultChoices(), 90).workouts)
        assertEquals(0, importer.import(a, a.defaultChoices(), 90).workouts)
        assertEquals(2, db.importDao().finishedWorkouts().size)
    }

    @Test
    fun firstSuccessfulImportPinsZoneAcrossDeviceZoneChangesAndRepositoryRecreation() = runTest {
        val original = TimeZone.getDefault()
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))
            val preview = importer.prepare(one())
            assertEquals("Asia/Manila", preview.timeZoneId)
            assertEquals(null, settings.hevyImportZone)
            assertEquals(1, importer.import(preview, preview.defaultChoices(), 90).workouts)
            assertEquals("Asia/Manila", settings.hevyImportZone)
            assertEquals("Asia/Manila", context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("hevy_import_zone", null))
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val again = ImportRepository(db, SettingsStore(context)).prepare(one())
            assertEquals("Asia/Manila", again.timeZoneId)
            assertEquals(preview.workouts.single().startedAt, again.workouts.single().startedAt)
            assertTrue(again.newWorkouts.isEmpty())
            assertEquals(0, importer.import(again, again.defaultChoices(), 90).workouts)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun failedTransactionDoesNotPinTheZone() = runTest {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON workouts BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val plan = importer.prepare(one())
        try {
            importer.import(plan, plan.defaultChoices(), 90)
            fail("Expected the insert to fail")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertEquals(null, settings.hevyImportZone)
            assertTrue(db.importDao().finishedWorkouts().isEmpty())
            assertTrue(db.importDao().mappings().isEmpty())
        }
    }

    @Test
    fun malformedQuoteAndInvalidNumbersLeaveExistingDatabaseUntouched() = runTest {
        importCsv(one())
        val backups = BackupRepository(db, now = { 10L })
        val before = BackupJson.write(backups.export(90))
        val badFiles = listOf(one("B") + ",\"unclosed", one("B", weight = "1e309"),
            one("B", weight = "Infinity"), one("B", weight = "NaN"), one("B", reps = "2147483648"))
        for (text in badFiles) {
            try {
                importCsv(text)
                fail("Expected a format error")
            } catch (e: HevyFormatException) {
                assertTrue(e.message!!, e.message!!.contains("Row 2"))
            }
            assertEquals(before, BackupJson.write(backups.export(90)))
        }
    }

    @Test
    fun successfulImportCanAlwaysBeExportedAsBackup() = runTest {
        assertEquals(3, importCsv(csv))
        assertEquals(1, importCsv(one("Planks", weight = "0", reps = "2147483647")))
        val backup = BackupRepository(db).export(90)
        assertEquals(4, BackupJson.read(BackupJson.write(backup)).workouts.size)
    }

    @Test
    fun plankImportTracksSecondsInFreshSeedAndAfterExistingInstallRepair() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db.clearAllTables()
        val exercises = ExerciseRepository(db.exerciseDao())
        exercises.seedIfEmpty(context, db)
        assertEquals(1, importCsv(one()))
        val plank = workouts.observeWorkout(db.importDao().finishedWorkouts().single().id).first()!!.blocks.single()
        assertEquals(TrackingType.DURATION, plank.exercise!!.trackingType)
        assertEquals(60, plank.sets.single().durationSec)

        db.openHelper.writableDatabase.execSQL("UPDATE exercises SET trackingType = 'BODYWEIGHT_REPS' WHERE id IN ('Plank', 'Side_Bridge')")
        val custom = db.exerciseDao().getByIds(listOf("Plank")).single().copy(id = "custom-hold", isCustom = true)
        db.exerciseDao().insert(custom)
        val beforeExercises = db.importDao().allExercises()
        val beforeWorkouts = db.importDao().finishedWorkouts()
        val beforeBlocks = db.importDao().allWorkoutExercises()
        val beforeSets = db.importDao().allWorkoutSets()
        exercises.seedIfEmpty(context, db)
        exercises.seedIfEmpty(context, db)
        assertEquals(beforeExercises.map {
            if (it.id in ExerciseSeed.trackingOverrides && !it.isCustom) it.copy(trackingType = TrackingType.DURATION) else it
        }, db.importDao().allExercises())
        assertEquals(beforeWorkouts, db.importDao().finishedWorkouts())
        assertEquals(beforeBlocks, db.importDao().allWorkoutExercises())
        assertEquals(beforeSets, db.importDao().allWorkoutSets())
        assertEquals(0, importCsv(one()))
        assertEquals(TrackingType.DURATION, db.exerciseDao().getByIds(listOf("Plank")).single().trackingType)
        assertEquals(60, db.importDao().allWorkoutSets().single().durationSec)
    }

    @Test
    fun repairDoesNotTouchACustomExerciseEvenWithAnOverrideId() = runTest {
        db.openHelper.writableDatabase.execSQL("UPDATE exercises SET isCustom = 1, trackingType = 'WEIGHT_REPS' WHERE id = 'Plank'")
        val before = db.exerciseDao().getByIds(listOf("Plank")).single()
        ExerciseRepository(db.exerciseDao()).seedIfEmpty(ApplicationProvider.getApplicationContext(), db)
        assertEquals(before, db.exerciseDao().getByIds(listOf("Plank")).single())
    }

    @Test
    fun renamingAnImportedWorkoutLosesTheOriginalTitleIdentity() = runTest {
        importCsv(one())
        val id = db.importDao().finishedWorkouts().single().id
        workouts.rename(id, "Renamed")
        assertEquals(1, importCsv(one()))
        assertEquals(setOf("A", "Renamed"), db.importDao().finishedWorkouts().map { it.title }.toSet())
    }

}
