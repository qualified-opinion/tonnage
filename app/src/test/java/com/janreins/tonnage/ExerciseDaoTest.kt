package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExerciseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ExerciseRepository
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = ExerciseRepository(db.exerciseDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedingRunsOnceAndKeepsCustomExercises() = runTest {
        repository.seedIfEmpty(context)
        val custom = repository.addCustom("Landmine Press", "shoulders", "other", TrackingType.WEIGHT_REPS)
        repository.seedIfEmpty(context)

        val all = repository.observeAll().first()
        assertEquals(877, all.size)
        assertTrue(all.any { it.id == custom.id && it.isCustom })
    }

    @Test
    fun onlyCustomExercisesCanBeDeleted() = runTest {
        db.exerciseDao().insertAll(
            listOf(Exercise(id = "Barbell_Squat", name = "Barbell Squat", category = "strength", equipment = "barbell", primaryMuscles = "quadriceps")),
        )
        val custom = repository.addCustom("Landmine Press", "shoulders", "other", TrackingType.WEIGHT_REPS)

        repository.deleteCustom("Barbell_Squat")
        repository.deleteCustom(custom.id)

        assertEquals(listOf("Barbell_Squat"), repository.observeAll().first().map { it.id })
    }
}
