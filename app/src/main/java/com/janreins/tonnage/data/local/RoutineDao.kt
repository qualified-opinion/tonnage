package com.janreins.tonnage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.janreins.tonnage.data.model.Routine
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RoutineDao {

    @Query("SELECT * FROM routines ORDER BY position, name COLLATE NOCASE")
    abstract fun observeRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM routine_exercises ORDER BY routineId, position")
    abstract fun observeAllRoutineExercises(): Flow<List<RoutineExercise>>

    @Query("SELECT * FROM routines WHERE id = :id")
    abstract suspend fun getRoutine(id: Long): Routine?

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY position")
    abstract suspend fun getExercises(routineId: Long): List<RoutineExercise>

    @Query(
        """
        SELECT rs.* FROM routine_sets rs
        INNER JOIN routine_exercises re ON rs.routineExerciseId = re.id
        WHERE re.routineId = :routineId
        ORDER BY rs.position
        """,
    )
    abstract suspend fun getSets(routineId: Long): List<RoutineSet>

    @Query("SELECT COALESCE(MAX(position), -1) FROM routines")
    abstract suspend fun maxPosition(): Int

    @Insert
    abstract suspend fun insertRoutine(routine: Routine): Long

    @Query("UPDATE routines SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM routines WHERE id = :id")
    abstract suspend fun deleteRoutine(id: Long)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    abstract suspend fun deleteExercises(routineId: Long)

    @Insert
    abstract suspend fun insertExercise(exercise: RoutineExercise): Long

    @Insert
    abstract suspend fun insertSet(set: RoutineSet): Long

    /** Inserts a new routine (id 0) or renames an existing one, then writes its contents. */
    @Transaction
    open suspend fun save(routine: Routine, exercises: List<Pair<RoutineExercise, List<RoutineSet>>>): Long {
        val id = if (routine.id == 0L || getRoutine(routine.id) == null) {
            insertRoutine(routine.copy(id = 0, position = maxPosition() + 1))
        } else {
            rename(routine.id, routine.name)
            routine.id
        }
        replaceContents(id, exercises)
        return id
    }

    /** Writes a routine's exercises and sets, replacing whatever it had before. */
    @Transaction
    open suspend fun replaceContents(routineId: Long, exercises: List<Pair<RoutineExercise, List<RoutineSet>>>) {
        deleteExercises(routineId)
        exercises.forEachIndexed { index, (exercise, sets) ->
            val exerciseId = insertExercise(exercise.copy(id = 0, routineId = routineId, position = index))
            sets.forEachIndexed { setIndex, set ->
                insertSet(set.copy(id = 0, routineExerciseId = exerciseId, position = setIndex))
            }
        }
    }
}
