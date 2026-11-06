package com.janreins.tonnage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.janreins.tonnage.data.model.BodyWeight
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BodyWeightDao {

    @Query("SELECT * FROM body_weights ORDER BY epochDay")
    abstract fun observeAll(): Flow<List<BodyWeight>>

    @Query("SELECT * FROM body_weights ORDER BY epochDay")
    abstract suspend fun all(): List<BodyWeight>

    @Query("SELECT * FROM body_weights WHERE epochDay = :epochDay")
    abstract suspend fun onDay(epochDay: Long): BodyWeight?

    @Insert
    abstract suspend fun insert(entry: BodyWeight): Long

    @Insert
    abstract suspend fun insertAll(entries: List<BodyWeight>)

    @Query("UPDATE body_weights SET weightKg = :weightKg WHERE id = :id")
    abstract suspend fun updateWeight(id: Long, weightKg: Double)

    /** Saves the weight for a day, replacing that day's entry (and keeping its id) if there is one. */
    @Transaction
    open suspend fun upsert(epochDay: Long, weightKg: Double, now: Long = System.currentTimeMillis()) {
        val existing = onDay(epochDay)
        if (existing == null) {
            insert(BodyWeight(epochDay = epochDay, weightKg = weightKg, createdAt = now))
        } else {
            updateWeight(existing.id, weightKg)
        }
    }

    @Query("DELETE FROM body_weights WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM body_weights")
    abstract suspend fun deleteAll()
}
