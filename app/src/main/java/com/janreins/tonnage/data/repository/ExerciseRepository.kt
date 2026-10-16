package com.janreins.tonnage.data.repository

import android.content.Context
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.local.ExerciseDao
import com.janreins.tonnage.data.local.ExerciseSeed
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.TrackingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ExerciseRepository(private val dao: ExerciseDao) {

    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()

    fun observe(id: String): Flow<Exercise?> = dao.observeById(id)

    /** Loads the bundled library on first launch. Safe to call every launch. */
    suspend fun seedIfEmpty(
        context: Context,
        database: AppDatabase = AppDatabase.getInstance(context),
    ) = withContext(Dispatchers.IO) {
        if (dao.builtInCount() == 0) {
            val json = context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() }
            dao.insertAll(ExerciseSeed.parse(json))
        }
        // Safe on every start: after the first repair this updates no rows.
        ExerciseSeed.trackingOverrides.entries.groupBy({ it.value }, { it.key }).forEach { (type, ids) ->
            database.importDao().repairBuiltInTracking(ids, type)
        }
    }

    suspend fun addCustom(
        name: String,
        primaryMuscle: String,
        equipment: String,
        trackingType: TrackingType,
    ): Exercise {
        val exercise = Exercise(
            id = "custom-" + UUID.randomUUID(),
            name = name.trim(),
            category = "custom",
            equipment = equipment,
            primaryMuscles = primaryMuscle,
            trackingType = trackingType,
            isCustom = true,
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(exercise)
        return exercise
    }

    /** Returns false when the exercise is used in a workout and was kept. */
    suspend fun deleteCustom(id: String): Boolean = dao.deleteCustom(id) > 0
}
