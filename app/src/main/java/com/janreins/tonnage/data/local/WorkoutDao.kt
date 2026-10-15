package com.janreins.tonnage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.janreins.tonnage.data.model.PastSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {

    // Workouts

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeActive(): Flow<Workout?>

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    abstract suspend fun getActive(): Workout?

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract suspend fun getWorkout(id: Long): Workout?

    @Insert
    abstract suspend fun insertWorkout(workout: Workout): Long

    @Query("UPDATE workouts SET title = :title WHERE id = :id")
    abstract suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE workouts SET endedAt = :endedAt WHERE id = :id")
    abstract suspend fun setEndedAt(id: Long, endedAt: Long)

    @Query("DELETE FROM workouts WHERE id = :id")
    abstract suspend fun deleteWorkout(id: Long)

    @Query(
        """
        SELECT w.id AS id, w.title AS title, w.startedAt AS startedAt, w.endedAt AS endedAt,
               COUNT(DISTINCT we.id) AS exerciseCount,
               COUNT(ws.id) AS setCount,
               COALESCE(SUM(CASE WHEN ws.type != 'WARMUP' THEN ws.weightKg * ws.reps END), 0) AS volumeKg
        FROM workouts w
        LEFT JOIN workout_exercises we ON we.workoutId = w.id
        LEFT JOIN workout_sets ws ON ws.workoutExerciseId = we.id AND ws.completed = 1
        WHERE w.endedAt IS NOT NULL
        GROUP BY w.id
        ORDER BY w.startedAt DESC
        """,
    )
    abstract fun observeFinishedSummaries(): Flow<List<WorkoutSummary>>

    // Exercises in a workout

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    abstract fun observeWorkoutExercises(workoutId: Long): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    abstract suspend fun getWorkoutExercises(workoutId: Long): List<WorkoutExercise>

    @Query("SELECT COALESCE(MAX(position), -1) FROM workout_exercises WHERE workoutId = :workoutId")
    abstract suspend fun maxExercisePosition(workoutId: Long): Int

    @Insert
    abstract suspend fun insertWorkoutExercise(workoutExercise: WorkoutExercise): Long

    @Query("UPDATE workout_exercises SET restSeconds = :restSeconds WHERE id = :id")
    abstract suspend fun updateRestSeconds(id: Long, restSeconds: Int)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    abstract suspend fun deleteWorkoutExercise(id: Long)

    // Sets

    @Query(
        """
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workout_exercises we ON ws.workoutExerciseId = we.id
        WHERE we.workoutId = :workoutId
        ORDER BY ws.position
        """,
    )
    abstract fun observeSetsForWorkout(workoutId: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY position")
    abstract suspend fun getSets(workoutExerciseId: Long): List<WorkoutSet>

    @Insert
    abstract suspend fun insertSet(set: WorkoutSet): Long

    @Query("UPDATE workout_sets SET weightKg = :weightKg WHERE id = :id")
    abstract suspend fun updateWeight(id: Long, weightKg: Double?)

    @Query("UPDATE workout_sets SET reps = :reps WHERE id = :id")
    abstract suspend fun updateReps(id: Long, reps: Int?)

    @Query("UPDATE workout_sets SET distanceKm = :distanceKm WHERE id = :id")
    abstract suspend fun updateDistance(id: Long, distanceKm: Double?)

    @Query("UPDATE workout_sets SET durationSec = :durationSec WHERE id = :id")
    abstract suspend fun updateDuration(id: Long, durationSec: Int?)

    @Query("UPDATE workout_sets SET type = :type WHERE id = :id")
    abstract suspend fun updateType(id: Long, type: SetType)

    @Query("UPDATE workout_sets SET completed = :completed, completedAt = :completedAt WHERE id = :id")
    abstract suspend fun updateCompleted(id: Long, completed: Boolean, completedAt: Long?)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    abstract suspend fun deleteSet(id: Long)

    @Query(
        """
        DELETE FROM workout_sets WHERE completed = 0
        AND workoutExerciseId IN (SELECT id FROM workout_exercises WHERE workoutId = :workoutId)
        """,
    )
    abstract suspend fun deleteIncompleteSets(workoutId: Long)

    @Query(
        """
        DELETE FROM workout_exercises WHERE workoutId = :workoutId
        AND id NOT IN (SELECT workoutExerciseId FROM workout_sets)
        """,
    )
    abstract suspend fun deleteEmptyExercises(workoutId: Long)

    /** Keeps only completed sets, drops exercises left empty, and moves the workout to history. */
    @Transaction
    open suspend fun finishWorkout(workoutId: Long, endedAt: Long) {
        deleteIncompleteSets(workoutId)
        deleteEmptyExercises(workoutId)
        setEndedAt(workoutId, endedAt)
    }

    @Query(
        """
        SELECT we.exerciseId AS exerciseId, ws.weightKg AS weightKg, ws.reps AS reps
        FROM workout_sets ws
        INNER JOIN workout_exercises we ON ws.workoutExerciseId = we.id
        INNER JOIN workouts w ON we.workoutId = w.id
        WHERE w.endedAt IS NOT NULL AND w.id != :excludeWorkoutId
          AND ws.completed = 1 AND ws.type != 'WARMUP'
          AND we.exerciseId IN (:exerciseIds)
        """,
    )
    abstract suspend fun pastWorkingSets(exerciseIds: List<String>, excludeWorkoutId: Long): List<PastSet>
}
