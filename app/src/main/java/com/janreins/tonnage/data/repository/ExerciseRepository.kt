package com.janreins.tonnage.data.repository

import android.content.Context
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.local.ExerciseSeed
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.TrackingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ExerciseRepository(database: AppDatabase) {
    private val dao = database.exerciseDao()
    private val importDao = database.importDao()

    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()

    fun observe(id: String): Flow<Exercise?> = dao.observeById(id)

    /**
     * Adds every bundled exercise the phone doesn't have yet, so a newer app's library reaches
     * existing installs. Rows already there (built-in or custom) are left alone. Run every launch.
     */
    suspend fun seedLibrary(context: Context) = withContext(Dispatchers.IO) {
        val json = context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() }
        seedLibrary(ExerciseSeed.parse(json))
    }

    /** Inserts the built-ins in [library] whose id is missing; [ExerciseDao.insertAll] ignores the rest. */
    internal suspend fun seedLibrary(library: List<Exercise>) = withContext(Dispatchers.IO) {
        dao.insertAll(library.filterNot { it.isCustom })
        // Safe on every start: after the first repair this updates no rows.
        ExerciseSeed.trackingOverrides.entries.groupBy({ it.value }, { it.key }).forEach { (type, ids) ->
            importDao.repairBuiltInTracking(ids, type)
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
