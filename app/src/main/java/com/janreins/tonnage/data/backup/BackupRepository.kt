package com.janreins.tonnage.data.backup

import androidx.room.withTransaction
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.RoutineContents

/** Restoring would delete the workout being logged, so it has to be finished or discarded first. */
class WorkoutInProgressException : Exception("Finish or discard the workout in progress before restoring a backup.")

class BackupRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.importDao()

    /** Reads everything worth keeping. The workout in progress, if any, isn't included. */
    suspend fun export(defaultRestSeconds: Int): BackupContents = db.withTransaction {
        val exercisesByWorkout = dao.allWorkoutExercises().groupBy { it.workoutId }
        val setsByExercise = dao.allWorkoutSets().groupBy { it.workoutExerciseId }
        val routineExercises = dao.allRoutineExercises().groupBy { it.routineId }
        val routineSets = dao.allRoutineSets().groupBy { it.routineExerciseId }
        BackupContents(
            exportedAt = now(),
            defaultRestSeconds = defaultRestSeconds,
            customExercises = dao.customExercises(),
            workouts = dao.finishedWorkouts().map { workout ->
                BackupWorkout(
                    workout,
                    exercisesByWorkout[workout.id].orEmpty().map { it to setsByExercise[it.id].orEmpty() },
                )
            },
            routines = dao.allRoutines().map { routine ->
                RoutineContents(routine, routineExercises[routine.id].orEmpty().map { it to routineSets[it.id].orEmpty() })
            },
            hevyMappings = dao.mappings(),
        )
    }

    /**
     * Replaces workouts, routines, custom exercises and remembered Hevy matches with the
     * backup's, in one transaction: if anything fails, nothing changes.
     */
    suspend fun restore(contents: BackupContents) = db.withTransaction {
        if (db.workoutDao().getActive() != null) throw WorkoutInProgressException()
        dao.deleteAllWorkouts()
        dao.deleteAllRoutines()
        dao.deleteAllMappings()
        dao.deleteCustomExercises()

        if (contents.customExercises.isNotEmpty()) dao.insertExercises(contents.customExercises)
        val routineDao = db.routineDao()
        contents.routines.forEach { routine ->
            // Keep routine ids so workouts stay linked to the routine they came from.
            val id = dao.insertRoutine(routine.routine)
            routineDao.replaceContents(id, routine.exercises)
        }
        val workoutDao = db.workoutDao()
        contents.workouts.forEach { workoutDao.insertWorkoutWithContents(it.workout.copy(id = 0), it.exercises) }
        if (contents.hevyMappings.isNotEmpty()) dao.saveMappings(contents.hevyMappings)
    }
}
