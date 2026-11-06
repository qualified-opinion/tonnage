package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.backup.BackupFormatException
import com.janreins.tonnage.data.backup.BackupJson
import com.janreins.tonnage.data.backup.BackupRepository
import com.janreins.tonnage.data.backup.BackupTooLargeException
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
import org.json.JSONArray
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

    private suspend fun assertRejectedWithoutChanges(
        text: String,
        message: String = "This file is incomplete or damaged. Nothing was changed.",
    ) {
        val before = backupOf(db)
        val exercisesBefore = db.importDao().allExercises()
        val error = runCatching { BackupRepository(db).restore(BackupJson.read(text)) }.exceptionOrNull()
        assertTrue("Expected a format error for $text, got $error", error is BackupFormatException)
        assertEquals(message, error?.message)
        assertEquals(before, backupOf(db))
        assertEquals(exercisesBefore, db.importDao().allExercises())
    }

    private fun JSONObject.firstWorkout() = getJSONArray("workouts").getJSONObject(0)
    private fun JSONObject.firstRoutine() = getJSONArray("routines").getJSONObject(0)
    private fun JSONObject.firstExercise() = getJSONArray("exercises").getJSONObject(0)
    private fun JSONObject.firstSet() = firstExercise().getJSONArray("sets").getJSONObject(0)

    @Test
    fun missingCustomExercisesCannotDeleteExistingData() = runTest {
        val damaged = JSONObject(BackupJson.write(backupOf(db))).apply { remove("customExercises") }
        assertRejectedWithoutChanges(damaged.toString())
    }

    @Test
    fun twoFieldFileIsRejectedWithoutChanges() = runTest {
        assertRejectedWithoutChanges("""{"format":"tonnage-backup","schemaVersion":1}""")
    }

    @Test
    fun everyTopLevelSectionAndMetadataFieldIsRequiredWithItsExportedType() = runTest {
        val text = BackupJson.write(backupOf(db))
        listOf("format", "schemaVersion", "exportedAt", "settings", "customExercises", "workouts", "routines", "hevyMappings").forEach { key ->
            assertRejectedWithoutChanges(JSONObject(text).apply { remove(key) }.toString())
            listOf(JSONObject.NULL, "wrong type", true, JSONObject()).forEach { wrong ->
                assertRejectedWithoutChanges(JSONObject(text).put(key, wrong).toString())
            }
        }
        listOf(JSONObject.NULL, "1", 1.5, true).forEach { wrong ->
            assertRejectedWithoutChanges(JSONObject(text).put("schemaVersion", wrong).toString())
        }
        assertRejectedWithoutChanges(JSONObject(text).apply { getJSONObject("settings").remove("defaultRestSeconds") }.toString())
    }

    @Test
    fun nestedExerciseAndSetArraysMustBePresentAndArrays() = runTest {
        val text = BackupJson.write(backupOf(db))
        listOf<(JSONObject) -> JSONObject>(
            { it.firstWorkout() }, { it.firstWorkout().firstExercise() },
            { it.firstRoutine() }, { it.firstRoutine().firstExercise() },
        ).forEachIndexed { index, parent ->
            val key = if (index % 2 == 0) "exercises" else "sets"
            assertRejectedWithoutChanges(JSONObject(text).apply { parent(this).remove(key) }.toString())
            listOf(JSONObject.NULL, JSONObject(), "wrong type", 1).forEach { wrong ->
                assertRejectedWithoutChanges(JSONObject(text).apply { parent(this).put(key, wrong) }.toString())
            }
            assertRejectedWithoutChanges(JSONObject(text).apply { parent(this).put(key, JSONArray().put(1)) }.toString())
        }
    }

    @Test
    fun infinityWeightIsRejectedWithoutChanges() = runTest {
        val damaged = JSONObject(BackupJson.write(backupOf(db)))
        damaged.firstWorkout().firstSet().put("weightKg", "Infinity")
        assertRejectedWithoutChanges(damaged.toString())
    }

    @Test
    fun nonFiniteNegativeAndWrongTypedSetNumbersAreRejectedWithoutChanges() = runTest {
        val text = BackupJson.write(backupOf(db))
        listOf<(JSONObject) -> JSONObject>({ it.firstWorkout().firstSet() }, { it.firstRoutine().firstSet() }).forEachIndexed { index, set ->
            val keys = listOf("weightKg", "distanceKm") + if (index == 0) listOf("rpe") else emptyList()
            keys.forEach { key ->
                listOf("\"Infinity\"", "\"-Infinity\"", "\"NaN\"", "1e309", "NaN", "-0.5", "true", "\"2.5\"").forEach { raw ->
                    val damaged = JSONObject(text).apply { set(this).put(key, "RAW_NUMBER") }
                    assertRejectedWithoutChanges(damaged.toString().replace("\"RAW_NUMBER\"", raw))
                }
            }
        }
    }

    @Test
    fun integerFieldsRejectFractionsNegativesOverflowAndWrongTypes() = runTest {
        val text = BackupJson.write(backupOf(db))
        val intFields = listOf<Pair<(JSONObject) -> JSONObject, String>>(
            { o: JSONObject -> o } to "schemaVersion",
            { o: JSONObject -> o.getJSONObject("settings") } to "defaultRestSeconds",
            { o: JSONObject -> o.firstWorkout().firstExercise() } to "restSeconds",
            { o: JSONObject -> o.firstWorkout().firstSet() } to "reps",
            { o: JSONObject -> o.firstWorkout().firstSet() } to "durationSec",
            { o: JSONObject -> o.firstRoutine() } to "position",
            { o: JSONObject -> o.firstRoutine().firstExercise() } to "restSeconds",
            { o: JSONObject -> o.firstRoutine().firstSet() } to "reps",
            { o: JSONObject -> o.firstRoutine().firstSet() } to "durationSec",
        )
        val longFields = listOf<Pair<(JSONObject) -> JSONObject, String>>(
            { o: JSONObject -> o } to "exportedAt",
            { o: JSONObject -> o.getJSONArray("customExercises").getJSONObject(0) } to "createdAt",
            { o: JSONObject -> o.firstWorkout() } to "startedAt",
            { o: JSONObject -> o.firstWorkout() } to "endedAt",
            { o: JSONObject -> o.firstWorkout() } to "routineId",
            { o: JSONObject -> o.firstWorkout().firstSet() } to "completedAt",
            { o: JSONObject -> o.firstRoutine() } to "id",
            { o: JSONObject -> o.firstRoutine() } to "createdAt",
        )
        (intFields + longFields).forEach { (parent, key) ->
            val overflow = if ((parent to key) in intFields) "2147483648" else "9223372036854775808"
            listOf("-1", "1.5", "1.0000000000000000001", overflow, "9223372036854775807.5", "\"1\"", "true").forEach { raw ->
                val damaged = JSONObject(text).apply { parent(this).put(key, "RAW_NUMBER") }
                assertRejectedWithoutChanges(damaged.toString().replace("\"RAW_NUMBER\"", raw))
            }
        }
    }

    @Test
    fun hugeWorkoutRoutineIdsAreRejectedWithoutChanges() = runTest {
        val text = BackupJson.write(backupOf(db))
        listOf(Long.MAX_VALUE, Int.MAX_VALUE.toLong() + 1).forEach { id ->
            val damaged = JSONObject(text).apply { firstWorkout().put("routineId", id) }
            assertRejectedWithoutChanges(damaged.toString())
        }
    }

    @Test
    fun hugeRoutineIdsAreRejectedWithoutChanges() = runTest {
        val text = BackupJson.write(backupOf(db))
        listOf(Long.MAX_VALUE, Int.MAX_VALUE.toLong() + 1).forEach { id ->
            val damaged = JSONObject(text).apply { firstRoutine().put("id", id) }
            assertRejectedWithoutChanges(damaged.toString())
        }
    }

    @Test
    fun zeroRoutineIdIsRejectedWithoutChanges() = runTest {
        val damaged = JSONObject(BackupJson.write(backupOf(db)))
        damaged.firstRoutine().put("id", 0)
        assertRejectedWithoutChanges(damaged.toString())
    }

    @Test
    fun zeroWorkoutRoutineIdIsRejectedWithoutChanges() = runTest {
        val damaged = JSONObject(BackupJson.write(backupOf(db)))
        damaged.firstWorkout().put("routineId", 0)
        assertRejectedWithoutChanges(damaged.toString())
    }

    @Test
    fun danglingWorkoutRoutineAndMappingReferencesAreRejectedBeforeDeletes() = runTest {
        forbidDeletes()
        val text = BackupJson.write(backupOf(db))
        listOf<(JSONObject) -> JSONObject>(
            { it.firstWorkout().firstExercise() }, { it.firstRoutine().firstExercise() },
            { it.getJSONArray("hevyMappings").getJSONObject(0) },
        ).forEach { reference ->
            // custom-1 already exists locally, but is absent from the incoming backup.
            assertRejectedWithoutChanges(JSONObject(text).apply {
                put("customExercises", JSONArray())
                reference(this).put("exerciseId", "custom-1")
            }.toString())
            assertRejectedWithoutChanges(JSONObject(text).apply { reference(this).put("exerciseId", "missing") }.toString())
        }
    }

    @Test
    fun duplicateCustomExerciseIdsAreRejectedBeforeDeletes() = runTest {
        forbidDeletes()
        val damaged = JSONObject(BackupJson.write(backupOf(db)))
        damaged.getJSONArray("customExercises").apply { put(getJSONObject(0)) }
        assertRejectedWithoutChanges(damaged.toString(), "The file has two custom exercises with the same id. Nothing was changed.")
    }

    @Test
    fun duplicateRoutineIdsAreRejectedBeforeDeletesWithAPlainMessage() = runTest {
        forbidDeletes()
        val damaged = JSONObject(BackupJson.write(backupOf(db)))
        damaged.getJSONArray("routines").apply { put(JSONObject(getJSONObject(0).toString()).put("name", "Push B")) }
        assertRejectedWithoutChanges(damaged.toString(), "The file has two routines with the same id. Nothing was changed.")
    }

    @Test
    fun aCustomExerciseWithABuiltInIdIsRejectedBeforeDeletes() = runTest {
        forbidDeletes()
        val damaged = JSONObject(BackupJson.write(backupOf(db)))
        damaged.getJSONArray("customExercises").put(JSONObject(damaged.getJSONArray("customExercises").getJSONObject(0).toString()).put("id", "bench"))
        assertRejectedWithoutChanges(
            damaged.toString(),
            "A custom exercise in the file has the same id as a built-in one. Nothing was changed.",
        )
    }

    @Test
    fun aBackupLargerThanRestoreAcceptsIsNotWritten() = runTest {
        val contents = backupOf(db)
        val size = BackupJson.write(contents).toByteArray().size
        assertEquals(size, BackupJson.encode(contents, limit = size).size)
        val error = runCatching { BackupJson.encode(contents, limit = size - 1) }.exceptionOrNull()
        assertTrue("Expected the size error, got $error", error is BackupTooLargeException)
        assertEquals("Your history is too large for one backup file (the limit is 50 MB), so no file was saved.", error?.message)
    }

    private fun forbidDeletes() {
        listOf("workouts", "routines", "hevy_mappings", "exercises", "body_weights").forEach { table ->
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER forbid_delete_$table BEFORE DELETE ON $table BEGIN SELECT RAISE(ABORT, 'Validation must precede deletes'); END",
            )
        }
    }

    @Test
    fun exportedNumericBoundariesAndOmittedNullableFieldsRemainReadable() = runTest {
        val original = backupOf(db)
        val contents = original.copy(
            exportedAt = Long.MAX_VALUE,
            defaultRestSeconds = Int.MAX_VALUE,
            workouts = original.workouts.map { w ->
                w.copy(
                    workout = w.workout.copy(routineId = Int.MAX_VALUE.toLong()),
                    exercises = w.exercises.map { (exercise, sets) ->
                        exercise to sets.map { it.copy(
                            weightKg = Double.MAX_VALUE, distanceKm = Double.MIN_VALUE, rpe = 0.0,
                            reps = Int.MAX_VALUE, durationSec = 0, completedAt = null,
                        ) }
                    },
                )
            },
        )
        val text = BackupJson.write(contents)
        val fresh = newDatabase()
        fresh.exerciseDao().insertAll(listOf(bench))
        BackupRepository(fresh).restore(BackupJson.read(text))
        val restored = BackupRepository(fresh, now = { Long.MAX_VALUE }).export(Int.MAX_VALUE)
        assertEquals(text, BackupJson.write(restored))
    }

    @Test
    fun currentExporterFixtureRestoresAndRoundTripsExactly() = runTest {
        // Captured from BackupJson.write on main 2037924, before changing the reader.
        val text = javaClass.getResource("/backup/current-export-v1.json")!!.readText()
        val fresh = newDatabase()
        fresh.exerciseDao().insertAll(listOf(bench))
        BackupRepository(fresh).restore(BackupJson.read(text))
        // The writer is now version 2 (body weights), so compare what the files hold.
        assertEquals(BackupJson.read(text), BackupJson.read(BackupJson.write(backupOf(fresh))).copy(bodyWeights = null))
    }

    @Test
    fun historicalRoutineIdsAreKeptReservedAndRoundTripExactly() = runTest {
        val original = backupOf(db)
        listOf(emptyList(), original.routines, original.routines.map { it.copy(routine = it.routine.copy(id = 100L)) }).forEach { routines ->
            val contents = original.copy(
                routines = routines,
                workouts = original.workouts.map { it.copy(workout = it.workout.copy(routineId = 42L)) },
            )
            val fresh = newDatabase()
            fresh.exerciseDao().insertAll(listOf(bench))
            val text = BackupJson.write(contents)
            BackupRepository(fresh).restore(BackupJson.read(text))
            assertEquals(text, BackupJson.write(backupOf(fresh)))
            val id = RoutineRepository(fresh.routineDao(), fresh.exerciseDao()).save(0, "New routine", emptyList())
            val highestReferencedId = maxOf(42L, routines.maxOfOrNull { it.routine.id } ?: 0L)
            assertTrue("Referenced routine ids must be reserved; got $id", id > highestReferencedId)
            assertEquals(42L, fresh.importDao().finishedWorkouts().single().routineId)
        }
    }

    @Test
    fun restoringNeverMovesAnExistingRoutineSequenceBackwards() = runTest {
        val contents = backupOf(db).copy(routines = emptyList())
        val dao = db.routineDao()
        val routine = db.importDao().allRoutines().single()
        dao.insertRoutine(routine.copy(id = 100L))
        dao.deleteRoutine(100L)
        BackupRepository(db).restore(contents)
        assertTrue(RoutineRepository(dao, db.exerciseDao()).save(0, "New routine", emptyList()) > 100L)
    }

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

    private fun day(date: String) = java.time.LocalDate.parse(date).toEpochDay()

    @Test
    fun bodyWeightsRoundTripInAVersion2Backup() = runTest {
        db.bodyWeightDao().upsert(day("2026-10-20"), 82.4)
        db.bodyWeightDao().upsert(day("2026-10-26"), 81.95)
        val text = BackupJson.write(backupOf(db))
        assertEquals(2, JSONObject(text).getInt("schemaVersion"))
        assertEquals(
            """[{"date":"2026-10-20","weightKg":82.4},{"date":"2026-10-26","weightKg":81.95}]""",
            JSONObject(text).getJSONArray("bodyWeights").toString(),
        )

        val fresh = newDatabase()
        fresh.exerciseDao().insertAll(listOf(bench))
        fresh.bodyWeightDao().upsert(day("2020-01-01"), 90.0)
        BackupRepository(fresh).restore(BackupJson.read(text))
        assertEquals(
            listOf("2026-10-20" to 82.4, "2026-10-26" to 81.95),
            fresh.bodyWeightDao().all().map { it.date.toString() to it.weightKg },
        )
        assertEquals(text, BackupJson.write(backupOf(fresh)))
    }

    @Test
    fun aVersion1RestoreLeavesBodyWeightsAlone() = runTest {
        db.bodyWeightDao().upsert(day("2026-10-20"), 82.4)
        val v1 = JSONObject(BackupJson.write(backupOf(db))).put("schemaVersion", 1)
        v1.remove("bodyWeights")
        val contents = BackupJson.read(v1.toString())
        assertNull(contents.bodyWeights)
        BackupRepository(db).restore(contents)
        assertEquals(listOf(82.4), db.bodyWeightDao().all().map { it.weightKg })
    }

    @Test
    fun badBodyWeightsAreRejectedBeforeDeletes() = runTest {
        db.bodyWeightDao().upsert(day("2026-10-20"), 82.4)
        forbidDeletes()
        val text = BackupJson.write(backupOf(db))
        fun withWeights(vararg entries: String) =
            JSONObject(text).put("bodyWeights", JSONArray("[" + entries.joinToString(",") + "]")).toString()
        val bad = "A body weight in the file isn't valid. Nothing was changed."
        assertRejectedWithoutChanges(withWeights("""{"date":"2026-10-20","weightKg":80}""", """{"date":"2026-10-20","weightKg":81}"""), BackupJson.DUPLICATE_BODY_WEIGHT)
        assertRejectedWithoutChanges(withWeights("""{"date":"2026-10-20","weightKg":0}"""), bad)
        assertRejectedWithoutChanges(withWeights("""{"date":"2026-10-20","weightKg":1000.5}"""), bad)
        assertRejectedWithoutChanges(withWeights("""{"date":"2026-13-40","weightKg":80}"""), bad)
        assertRejectedWithoutChanges(withWeights("""{"date":20261020,"weightKg":80}"""))
        assertRejectedWithoutChanges(withWeights("""{"date":"2026-10-20","weightKg":-80}"""))
        assertRejectedWithoutChanges(JSONObject(text).apply { remove("bodyWeights") }.toString())
        assertEquals(listOf(82.4), db.bodyWeightDao().all().map { it.weightKg })
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
