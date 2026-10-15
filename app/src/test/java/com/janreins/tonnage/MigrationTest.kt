package com.janreins.tonnage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Builds a version 1 database (exercises only, as shipped in the first APK) and opens it with
 * the current app. Room checks the migrated tables against what the app expects, so a wrong
 * column or index in MIGRATION_1_2 fails here.
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

    @Test
    fun version1ExercisesSurviveTheUpgrade() = runTest {
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
}
