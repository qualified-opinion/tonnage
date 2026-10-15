package com.janreins.tonnage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.janreins.tonnage.data.local.ExerciseSeed
import com.janreins.tonnage.data.model.ExerciseOptions
import com.janreins.tonnage.data.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExerciseSeedTest {

    private val exercises by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() }
        ExerciseSeed.parse(json)
    }

    @Test
    fun bundledLibraryLoadsWithUniqueIds() {
        assertEquals(876, exercises.size)
        assertEquals(exercises.size, exercises.map { it.id }.toSet().size)
        assertTrue(exercises.all { it.name.isNotBlank() && it.primaryMuscleList.isNotEmpty() })
    }

    @Test
    fun everyMuscleAndEquipmentValueIsAFilterOption() {
        val muscles = exercises.flatMap { it.primaryMuscleList + it.secondaryMuscleList }.toSet()
        val equipment = exercises.map { it.equipment }.toSet()
        assertTrue("Unknown muscles: ${muscles - ExerciseOptions.muscles.toSet()}", ExerciseOptions.muscles.containsAll(muscles))
        assertTrue("Unknown equipment: ${equipment - ExerciseOptions.equipment.toSet()}", ExerciseOptions.equipment.containsAll(equipment))
    }

    @Test
    fun trackingTypeFollowsCategoryAndEquipment() {
        val byName = exercises.associateBy { it.name }
        assertEquals(TrackingType.WEIGHT_REPS, byName.getValue("Barbell Squat").trackingType)
        assertEquals(TrackingType.BODYWEIGHT_REPS, byName.getValue("Pullups").trackingType)
        assertEquals(TrackingType.DURATION, ExerciseSeed.trackingTypeFor("stretching", "body only"))
        assertEquals(TrackingType.DISTANCE_DURATION, ExerciseSeed.trackingTypeFor("cardio", "machine"))
    }
}
