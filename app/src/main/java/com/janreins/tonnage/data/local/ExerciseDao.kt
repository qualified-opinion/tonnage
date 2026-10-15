package com.janreins.tonnage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.janreins.tonnage.data.model.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<Exercise?>

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 0")
    suspend fun builtInCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: Exercise)

    /** Deletes a custom exercise unless a workout or routine uses it. Returns the number of rows deleted. */
    @Query(
        """
        DELETE FROM exercises WHERE id = :id AND isCustom = 1
        AND id NOT IN (SELECT exerciseId FROM workout_exercises)
        AND id NOT IN (SELECT exerciseId FROM routine_exercises)
        """,
    )
    suspend fun deleteCustom(id: String): Int
}
