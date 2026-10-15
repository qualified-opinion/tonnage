package com.janreins.tonnage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.Routine
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet

/** Adds workouts, their exercises and sets. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workouts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `endedAt` INTEGER,
                `notes` TEXT NOT NULL,
                `source` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_endedAt` ON `workouts` (`endedAt`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_exercises` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `workoutId` INTEGER NOT NULL,
                `exerciseId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `restSeconds` INTEGER NOT NULL,
                `notes` TEXT NOT NULL,
                FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercises_workoutId` ON `workout_exercises` (`workoutId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercises_exerciseId` ON `workout_exercises` (`exerciseId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_sets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `workoutExerciseId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `weightKg` REAL,
                `reps` INTEGER,
                `distanceKm` REAL,
                `durationSec` INTEGER,
                `rpe` REAL,
                `completed` INTEGER NOT NULL,
                `completedAt` INTEGER,
                FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_workoutExerciseId` ON `workout_sets` (`workoutExerciseId`)")
    }
}

/** Adds routines (templates) and links workouts to the routine they were started from. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `workouts` ADD COLUMN `routineId` INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `routines` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `routine_exercises` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `routineId` INTEGER NOT NULL,
                `exerciseId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `restSeconds` INTEGER NOT NULL,
                FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `routine_sets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `routineExerciseId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `weightKg` REAL,
                `reps` INTEGER,
                `distanceKm` REAL,
                `durationSec` INTEGER,
                FOREIGN KEY(`routineExerciseId`) REFERENCES `routine_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_sets_routineExerciseId` ON `routine_sets` (`routineExerciseId`)")
    }
}

@Database(
    entities = [
        Exercise::class, Workout::class, WorkoutExercise::class, WorkoutSet::class,
        Routine::class, RoutineExercise::class, RoutineSet::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun routineDao(): RoutineDao

    companion object {
        const val NAME = "tonnage_database"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
