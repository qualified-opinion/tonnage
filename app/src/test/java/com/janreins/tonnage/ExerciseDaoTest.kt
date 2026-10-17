package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.local.ExerciseSeed
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
        repository = ExerciseRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedingRunsOnceAndKeepsCustomExercises() = runTest {
        repository.seedLibrary(context)
        val custom = repository.addCustom("Landmine Press", "shoulders", "other", TrackingType.WEIGHT_REPS)
        repository.seedLibrary(context)

        val all = repository.observeAll().first()
        assertEquals(877, all.size)
        assertTrue(all.any { it.id == custom.id && it.isCustom })
    }

    @Test
    fun seedingAddsNewLibraryExercisesWithoutTouchingExistingRows() = runTest {
        val library = ExerciseSeed.parse(context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() })
        // An install from an older app with a shorter library, one built-in edited and a custom exercise.
        repository.addMissingBuiltIns(library.take(100))
        db.openHelper.writableDatabase.execSQL("UPDATE exercises SET name = 'Edited' WHERE id = '${library[0].id}'")
        val custom = repository.addCustom("Landmine Press", "shoulders", "other", TrackingType.WEIGHT_REPS)
        assertEquals(101, repository.observeAll().first().size)

        repository.seedLibrary(context)

        val all = repository.observeAll().first().associateBy { it.id }
        assertEquals(library.size + 1, all.size)
        assertTrue(library.all { it.id in all })
        assertEquals("Edited", all.getValue(library[0].id).name)
        assertEquals(custom, all.getValue(custom.id))
    }

    @Test
    fun seedingRepairsTheInjectedDatabase() = runTest {
        repository.seedLibrary(context)
        db.openHelper.writableDatabase.execSQL("UPDATE exercises SET trackingType = 'BODYWEIGHT_REPS' WHERE id = 'Plank'")
        repository.seedLibrary(context)
        assertEquals(TrackingType.DURATION, db.exerciseDao().getByIds(listOf("Plank")).single().trackingType)
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
