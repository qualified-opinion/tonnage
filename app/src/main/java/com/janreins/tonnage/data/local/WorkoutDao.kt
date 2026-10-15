package com.janreins.tonnage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.janreins.tonnage.data.model.PastSet
import com.janreins.tonnage.data.model.RecordSet
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

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract fun observeWorkout(id: Long): Flow<Workout?>

    @Insert
    abstract suspend fun insertWorkout(workout: Workout): Long

    /** Inserts a workout with its exercises and sets in one go (starting from a routine). */
    @Transaction
    open suspend fun insertWorkoutWithContents(
        workout: Workout,
        exercises: List<Pair<WorkoutExercise, List<WorkoutSet>>>,
    ): Long {
        val workoutId = insertWorkout(workout)
        exercises.forEachIndexed { index, (exercise, sets) ->
            val exerciseId = insertWorkoutExercise(exercise.copy(id = 0, workoutId = workoutId, position = index))
            sets.forEachIndexed { setIndex, set ->
                insertSet(set.copy(id = 0, workoutExerciseId = exerciseId, position = setIndex))
            }
        }
        return workoutId
    }

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

    @Query("UPDATE workout_exercises SET position = :position WHERE id = :id")
    abstract suspend fun updateExercisePosition(id: Long, position: Int)

    /** Swaps an exercise with its neighbour; [direction] is -1 (up) or +1 (down). */
    @Transaction
    open suspend fun moveExercise(workoutId: Long, workoutExerciseId: Long, direction: Int) {
        val ordered = getWorkoutExercises(workoutId)
        val index = ordered.indexOfFirst { it.id == workoutExerciseId }
        val target = index + direction
        if (index < 0 || target !in ordered.indices) return
        val reordered = ordered.toMutableList().apply { add(target, removeAt(index)) }
        reordered.forEachIndexed { position, exercise -> updateExercisePosition(exercise.id, position) }
    }

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

    @Query(
        """
        UPDATE workout_sets SET weightKg = :weightKg, reps = :reps, distanceKm = :distanceKm, durationSec = :durationSec
        WHERE id = :id
        """,
    )
    abstract suspend fun updateValues(id: Long, weightKg: Double?, reps: Int?, distanceKm: Double?, durationSec: Int?)

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

    @Query(
        """
        DELETE FROM workout_sets
        WHERE weightKg IS NULL AND reps IS NULL AND distanceKm IS NULL AND durationSec IS NULL
        AND workoutExerciseId IN (SELECT id FROM workout_exercises WHERE workoutId = :workoutId)
        """,
    )
    abstract suspend fun deleteBlankSets(workoutId: Long)

    /** After editing a finished workout: drops sets left blank and exercises left empty. */
    @Transaction
    open suspend fun tidyEdited(workoutId: Long) {
        deleteBlankSets(workoutId)
        deleteEmptyExercises(workoutId)
    }

    /** Keeps only completed sets, drops exercises left empty, and moves the workout to history. */
    @Transaction
    open suspend fun finishWorkout(workoutId: Long, endedAt: Long) {
        deleteIncompleteSets(workoutId)
        deleteEmptyExercises(workoutId)
        setEndedAt(workoutId, endedAt)
    }

    /** The most recent finished workout containing the exercise, optionally limited to one routine. */
    @Query(
        """
        SELECT w.id FROM workouts w
        INNER JOIN workout_exercises we ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId AND w.endedAt IS NOT NULL
          AND (:routineId IS NULL OR w.routineId = :routineId)
          AND EXISTS (SELECT 1 FROM workout_sets ws WHERE ws.workoutExerciseId = we.id AND ws.completed = 1)
        ORDER BY w.startedAt DESC
        LIMIT 1
        """,
    )
    abstract suspend fun latestWorkoutWith(exerciseId: String, routineId: Long?): Long?

    /** Completed sets of the exercise's first logged appearance in a workout, in order. */
    @Query(
        """
        SELECT ws.* FROM workout_sets ws
        WHERE ws.completed = 1 AND ws.workoutExerciseId = (
            SELECT we.id FROM workout_exercises we
            WHERE we.workoutId = :workoutId AND we.exerciseId = :exerciseId
              AND EXISTS (SELECT 1 FROM workout_sets s WHERE s.workoutExerciseId = we.id AND s.completed = 1)
            ORDER BY we.position LIMIT 1
        )
        ORDER BY ws.position
        """,
    )
    abstract suspend fun completedSetsOf(workoutId: Long, exerciseId: String): List<WorkoutSet>

    @Query(
        """
        SELECT w.id AS workoutId, w.startedAt AS startedAt, we.exerciseId AS exerciseId,
               e.name AS exerciseName, e.trackingType AS trackingType,
               ws.id AS setId, ws.weightKg AS weightKg, ws.reps AS reps
        FROM workout_sets ws
        INNER JOIN workout_exercises we ON ws.workoutExerciseId = we.id
        INNER JOIN workouts w ON we.workoutId = w.id
        LEFT JOIN exercises e ON e.id = we.exerciseId
        WHERE w.endedAt IS NOT NULL AND ws.completed = 1 AND ws.type != 'WARMUP'
        ORDER BY w.startedAt, w.id, we.position, ws.position
        """,
    )
    abstract fun observeRecordSets(): Flow<List<RecordSet>>

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
