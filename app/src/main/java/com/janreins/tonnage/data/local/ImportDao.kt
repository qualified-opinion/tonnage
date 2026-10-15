package com.janreins.tonnage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.HevyMapping
import com.janreins.tonnage.data.model.Routine
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet

/** Bulk reads and writes for the Hevy import and for backup / restore. */
@Dao
interface ImportDao {

    @Query("SELECT * FROM hevy_mappings")
    suspend fun mappings(): List<HevyMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMappings(mappings: List<HevyMapping>)

    /** Start times of workouts already imported from Hevy, to skip them on a second import. */
    @Query("SELECT startedAt FROM workouts WHERE source = 'hevy'")
    suspend fun hevyStartTimes(): List<Long>

    @Query("SELECT * FROM exercises")
    suspend fun allExercises(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE isCustom = 1 ORDER BY createdAt")
    suspend fun customExercises(): List<Exercise>

    @Query("SELECT * FROM workouts WHERE endedAt IS NOT NULL ORDER BY startedAt")
    suspend fun finishedWorkouts(): List<Workout>

    @Query("SELECT * FROM workout_exercises ORDER BY workoutId, position")
    suspend fun allWorkoutExercises(): List<WorkoutExercise>

    @Query("SELECT * FROM workout_sets ORDER BY workoutExerciseId, position")
    suspend fun allWorkoutSets(): List<WorkoutSet>

    @Query("SELECT * FROM routines ORDER BY position")
    suspend fun allRoutines(): List<Routine>

    @Query("SELECT * FROM routine_exercises ORDER BY routineId, position")
    suspend fun allRoutineExercises(): List<RoutineExercise>

    @Query("SELECT * FROM routine_sets ORDER BY routineExerciseId, position")
    suspend fun allRoutineSets(): List<RoutineSet>

    @Insert
    suspend fun insertExercises(exercises: List<Exercise>)

    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkouts()

    @Query("DELETE FROM routines")
    suspend fun deleteAllRoutines()

    @Query("DELETE FROM hevy_mappings")
    suspend fun deleteAllMappings()

    @Query("DELETE FROM exercises WHERE isCustom = 1")
    suspend fun deleteCustomExercises()
}
