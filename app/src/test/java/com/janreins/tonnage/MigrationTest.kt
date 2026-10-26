package com.janreins.tonnage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.HevyMapping
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.repository.RoutineRepository
import com.janreins.tonnage.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Builds older databases by hand (as shipped in earlier APKs) and opens them with the current
 * app. Room checks the migrated tables against what the app expects, so a wrong column or
 * index in any migration fails here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** Version 1: exercises only, as shipped in the first APK. */
    private fun createVersion1() {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`equipment` TEXT NOT NULL, `primaryMuscles` TEXT NOT NULL, `secondaryMuscles` TEXT NOT NULL, " +
                    "`mechanic` TEXT NOT NULL, `instructions` TEXT NOT NULL, `trackingType` TEXT NOT NULL, " +
                    "`isCustom` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            v1.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")
            v1.execSQL(
                "INSERT INTO exercises VALUES ('custom-1', 'Landmine Press', 'custom', 'other', 'shoulders', '', '', '', " +
                    "'WEIGHT_REPS', 1, 1700000000000)",
            )
            v1.version = 1
        }
    }

    /** Version 2: the phase 2 APK's tables, with one finished workout logged. */
    private fun createVersion2() {
        createVersion1()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { v2 ->
            v2.execSQL(
                "CREATE TABLE IF NOT EXISTS `workouts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `notes` TEXT NOT NULL, `source` TEXT NOT NULL)",
            )
            v2.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_endedAt` ON `workouts` (`endedAt`)")
            v2.execSQL(
                "CREATE TABLE IF NOT EXISTS `workout_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`workoutId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`restSeconds` INTEGER NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            v2.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercises_workoutId` ON `workout_exercises` (`workoutId`)")
            v2.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercises_exerciseId` ON `workout_exercises` (`exerciseId`)")
            v2.execSQL(
                "CREATE TABLE IF NOT EXISTS `workout_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`workoutExerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `type` TEXT NOT NULL, `weightKg` REAL, " +
                    "`reps` INTEGER, `distanceKm` REAL, `durationSec` INTEGER, `rpe` REAL, `completed` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER, FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            v2.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_workoutExerciseId` ON `workout_sets` (`workoutExerciseId`)")
            v2.execSQL("INSERT INTO workouts VALUES (1, 'Push', 1000, 2000, '', 'app')")
            v2.execSQL("INSERT INTO workout_exercises VALUES (1, 1, 'custom-1', 0, 90, '')")
            v2.execSQL("INSERT INTO workout_sets VALUES (1, 1, 0, 'NORMAL', 40.0, 10, NULL, NULL, NULL, 1, 1500)")
            v2.version = 2
        }
    }

    /** Version 3: the phase 3 APK's tables (routines, workouts.routineId), with a routine and a workout started from it. */
    private fun createVersion3() {
        createVersion2()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { v3 ->
            v3.execSQL("ALTER TABLE `workouts` ADD COLUMN `routineId` INTEGER")
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`notes` TEXT NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS `routine_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`routineId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`restSeconds` INTEGER NOT NULL, FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            v3.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)")
            v3.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)")
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS `routine_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`routineExerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `type` TEXT NOT NULL, `weightKg` REAL, " +
                    "`reps` INTEGER, `distanceKm` REAL, `durationSec` INTEGER, FOREIGN KEY(`routineExerciseId`) " +
                    "REFERENCES `routine_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            v3.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_sets_routineExerciseId` ON `routine_sets` (`routineExerciseId`)")
            v3.execSQL("INSERT INTO routines VALUES (7, 'Push A', 'Heavy day', 0, 1700000000000)")
            v3.execSQL("INSERT INTO routine_exercises VALUES (1, 7, 'custom-1', 0, 120)")
            v3.execSQL("INSERT INTO routine_sets VALUES (1, 1, 0, 'WARMUP', 20.0, 12, NULL, NULL)")
            v3.execSQL("INSERT INTO workouts VALUES (2, 'Push A', 500, 800, '', 'app', 7)")
            v3.execSQL("INSERT INTO workout_exercises VALUES (2, 2, 'custom-1', 0, 120, '')")
            v3.execSQL("INSERT INTO workout_sets VALUES (2, 2, 0, 'NORMAL', 42.5, 8, NULL, NULL, NULL, 1, 700)")
            v3.version = 3
        }
    }

    /** Version 4: the 1.x APKs' tables (adds remembered Hevy matches), with one saved. */
    private fun createVersion4() {
        createVersion3()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { v4 ->
            v4.execSQL(
                "CREATE TABLE IF NOT EXISTS `hevy_mappings` (`hevyName` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, PRIMARY KEY(`hevyName`))",
            )
            v4.execSQL("INSERT INTO hevy_mappings VALUES ('landmine press', 'custom-1')")
            v4.version = 4
        }
    }

    @Test
    fun version4DataSurvivesAndBodyWeightsWork() = runTest {
        createVersion4()
        val db = AppDatabase.build(context, name)
        try {
            assertEquals(listOf("Push", "Push A"), listOf(1L, 2L).map { db.workoutDao().getWorkout(it)!!.title })
            assertEquals(listOf(40.0, 42.5), listOf(1L, 2L).map { db.workoutDao().getSet(it)!!.weightKg })
            assertEquals("Push A", db.routineDao().getRoutine(7)!!.name)
            assertEquals(Triple(SetType.WARMUP, 20.0, 12), db.routineDao().getSets(7).single().let { Triple(it.type, it.weightKg, it.reps) })
            assertEquals("Landmine Press", db.exerciseDao().observeById("custom-1").first()!!.name)
            assertEquals(listOf(HevyMapping("landmine press", "custom-1")), db.importDao().mappings())

            val bodyWeights = db.bodyWeightDao()
            assertEquals(emptyList<Any>(), bodyWeights.all())
            bodyWeights.upsert(epochDay = 20_000, weightKg = 82.4)
            bodyWeights.upsert(epochDay = 20_000, weightKg = 81.9)
            assertEquals(listOf(20_000L to 81.9), bodyWeights.all().map { it.epochDay to it.weightKg })
        } finally {
            db.close()
        }
    }

    @Test
    fun version3RoutinesAndHistorySurviveAndHevyMatchesWork() = runTest {
        createVersion3()
        val db = AppDatabase.build(context, name)
        try {
            assertEquals(listOf("Push", "Push A"), listOf(1L, 2L).map { db.workoutDao().getWorkout(it)!!.title })
            assertEquals(7L, db.workoutDao().getWorkout(2)!!.routineId)

            val routine = db.routineDao().getRoutine(7)!!
            assertEquals("Push A" to "Heavy day", routine.name to routine.notes)
            assertEquals(listOf(120), db.routineDao().getExercises(7).map { it.restSeconds })
            val set = db.routineDao().getSets(7).single()
            assertEquals(Triple(SetType.WARMUP, 20.0, 12), Triple(set.type, set.weightKg, set.reps))

            // "Previous" prefers the routine's last workout over the later one without a routine.
            val previous = WorkoutRepository(db.workoutDao(), db.exerciseDao()).previousSets(listOf("custom-1"), routineId = 7)
            assertEquals(listOf(42.5 to 8), previous.getValue("custom-1").map { it.weightKg to it.reps })

            db.importDao().saveMappings(listOf(HevyMapping("landmine press", "custom-1")))
            assertEquals(listOf("custom-1"), db.importDao().mappings().map { it.exerciseId })
        } finally {
            db.close()
        }
    }

    @Test
    fun version1ExercisesSurviveTheUpgrade() = runTest {
        createVersion1()
        val db = AppDatabase.build(context, name)
        try {
            val custom = db.exerciseDao().observeById("custom-1").first()
            assertNotNull(custom)
            assertEquals("Landmine Press", custom!!.name)

            val workoutId = db.workoutDao().insertWorkout(Workout(title = "Test", startedAt = 1L))
            assertEquals(workoutId, db.workoutDao().getActive()!!.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun version2WorkoutsSurviveAndRoutinesWork() = runTest {
        createVersion2()
        val db = AppDatabase.build(context, name)
        try {
            val workout = db.workoutDao().getWorkout(1)!!
            assertEquals("Push", workout.title)
            assertNull(workout.routineId)

            // Logged history feeds the "previous" column after the upgrade.
            val previous = WorkoutRepository(db.workoutDao(), db.exerciseDao()).previousSets(listOf("custom-1"), routineId = null)
            assertEquals(listOf(40.0 to 10), previous.getValue("custom-1").map { it.weightKg to it.reps })

            val routines = RoutineRepository(db.routineDao(), db.exerciseDao())
            val routineId = routines.save(
                0,
                "Push A",
                listOf(RoutineExercise(routineId = 0, exerciseId = "custom-1", position = 0, restSeconds = 90) to listOf(RoutineSet(routineExerciseId = 0, position = 0))),
            )
            assertEquals(listOf("Landmine Press"), routines.observeSummaries().first().single { it.routine.id == routineId }.exerciseNames)

            // Version 4 added remembered Hevy matches.
            db.importDao().saveMappings(listOf(HevyMapping("landmine press", "custom-1")))
            assertEquals(listOf("custom-1"), db.importDao().mappings().map { it.exerciseId })
            assertEquals(emptyList<Long>(), db.importDao().hevyWorkouts().map { it.startedAt })
        } finally {
            db.close()
        }
    }
}
