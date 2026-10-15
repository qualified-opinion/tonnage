package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.backup.BackupFormatException
import com.janreins.tonnage.data.backup.BackupJson
import com.janreins.tonnage.data.backup.BackupRepository
import com.janreins.tonnage.data.backup.WorkoutInProgressException
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.HevyMapping
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.repository.RoutineRepository
import com.janreins.tonnage.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opened = mutableListOf<AppDatabase>()
    private lateinit var db: AppDatabase

    private val bench = Exercise(id = "bench", name = "Bench Press", category = "strength", equipment = "barbell", primaryMuscles = "chest")
    private val landmine = Exercise(
        id = "custom-1", name = "Landmine Press", category = "custom", equipment = "other", primaryMuscles = "shoulders",
        trackingType = TrackingType.WEIGHT_REPS, isCustom = true, createdAt = 1_000L,
    )

    private fun newDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build().also {
            opened += it
        }

    @Before
    fun setUp() = runTest {
        db = newDatabase()
        db.exerciseDao().insertAll(listOf(bench, landmine))
        val workouts = WorkoutRepository(db.workoutDao(), db.exerciseDao(), now = { 10_000L })

        val id = workouts.startEmpty()
        workouts.rename(id, "Push")
        workouts.addExercises(id, listOf("bench", "custom-1"), restSeconds = 120)
        val blocks = workouts.observeActive().first()!!.blocks
        val set = blocks[0].sets.single()
        workouts.setType(set.id, SetType.WARMUP)
        workouts.setWeight(set.id, 40.0)
        workouts.setReps(set.id, 10)
        workouts.setCompleted(set.id, true)
        workouts.addSet(blocks[0].workoutExercise.id, completed = true)
        val landmineSet = blocks[1].sets.single().id
        workouts.setWeight(landmineSet, 22.5)
        workouts.setReps(landmineSet, 8)
        workouts.setCompleted(landmineSet, true)
        workouts.finish(id)

        RoutineRepository(db.routineDao(), db.exerciseDao()).save(
            0,
            "Push A",
            listOf(
                RoutineExercise(routineId = 0, exerciseId = "custom-1", position = 0, restSeconds = 90) to
                    listOf(RoutineSet(routineExerciseId = 0, position = 0, weightKg = 20.0, reps = 12)),
            ),
        )
        db.importDao().saveMappings(listOf(HevyMapping("landmine press", "custom-1")))
    }

    @After
    fun tearDown() = opened.forEach { it.close() }

    private suspend fun backupOf(database: AppDatabase) = BackupRepository(database, now = { 99L }).export(defaultRestSeconds = 75)

    @Test
    fun aBackupRestoresEverythingIntoAFreshInstall() = runTest {
        val text = BackupJson.write(backupOf(db))
        assertEquals(BackupJson.SCHEMA_VERSION, JSONObject(text).getInt("schemaVersion"))

        val fresh = newDatabase()
        fresh.exerciseDao().insertAll(listOf(bench))
        val contents = BackupJson.read(text)
        assertEquals(75, contents.defaultRestSeconds)
        BackupRepository(fresh).restore(contents)

        assertEquals(text, BackupJson.write(backupOf(fresh)))
        val history = WorkoutRepository(fresh.workoutDao(), fresh.exerciseDao()).observeHistory().first()
        assertEquals(listOf("Push"), history.map { it.title })
        assertEquals(3, history.single().setCount)
        assertEquals("Landmine Press", fresh.exerciseDao().observeById("custom-1").first()?.name)
    }

    @Test
    fun restoringReplacesWhatWasThere() = runTest {
        val contents = backupOf(db)
        val workouts = WorkoutRepository(db.workoutDao(), db.exerciseDao())
        workouts.finish(workouts.startEmpty())
        db.importDao().saveMappings(listOf(HevyMapping("squat", "bench")))

        BackupRepository(db).restore(contents)

        assertEquals(listOf("Push"), workouts.observeHistory().first().map { it.title })
        assertEquals(listOf("landmine press"), db.importDao().mappings().map { it.hevyName })
        assertEquals(1, db.importDao().allRoutines().size)
    }

    @Test
    fun restoreWaitsUntilTheWorkoutInProgressIsDone() = runTest {
        val contents = backupOf(db)
        val workouts = WorkoutRepository(db.workoutDao(), db.exerciseDao())
        val active = workouts.startEmpty()
        try {
            BackupRepository(db).restore(contents)
            fail("Restore should be refused while a workout is in progress")
        } catch (expected: WorkoutInProgressException) {
        }
        assertEquals(active, workouts.getActive()?.id)
        assertEquals(1, workouts.observeHistory().first().size)
    }

    @Test
    fun otherFilesAndNewerBackupsAreRejected() = runTest {
        val text = BackupJson.write(backupOf(db))
        val newer = JSONObject(text).put("schemaVersion", BackupJson.SCHEMA_VERSION + 1).toString()
        listOf("not json", "{}", """{"format":"something-else","schemaVersion":1}""", newer).forEach { bad ->
            val error = runCatching { BackupJson.read(bad) }.exceptionOrNull()
            assertTrue("Expected a format error for $bad", error is BackupFormatException)
        }
        assertTrue(
            runCatching { BackupJson.read(newer) }.exceptionOrNull()!!.message!!.contains("newer version"),
        )
        assertNull(runCatching { BackupJson.read(text) }.exceptionOrNull())
    }
}
