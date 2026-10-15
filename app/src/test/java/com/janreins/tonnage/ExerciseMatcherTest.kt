package com.janreins.tonnage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.hevy.ExerciseMatcher
import com.janreins.tonnage.data.hevy.HevyAliases
import com.janreins.tonnage.data.hevy.MatchConfidence
import com.janreins.tonnage.data.local.ExerciseSeed
import com.janreins.tonnage.data.model.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExerciseMatcherTest {

    private val library: List<Exercise> by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ExerciseSeed.parse(context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() })
    }
    private val byId by lazy { library.associateBy { it.id } }

    @Test
    fun everyAliasPointsAtALibraryExercise() {
        val missing = HevyAliases.byHevyName.filterValues { it !in byId }
        assertTrue("Unknown ids: $missing", missing.isEmpty())
    }

    @Test
    fun aRealHevyExportMostlyMatchesWithoutReview() {
        // The 37 exercise names in Jan's Hevy export (October 2026).
        val names = listOf(
            "Bench Press (Barbell)", "Bent Over Row (Dumbbell)", "Bicep Curl (Dumbbell)", "Chest Fly (Machine)",
            "Cross Body Hammer Curl", "Deadlift (Barbell)", "EZ Bar Biceps Curl", "Face Pull", "Hammer Curl (Dumbbell)",
            "Incline Bench Press (Barbell)", "Incline Bench Press (Dumbbell)", "Incline Push Ups", "Kneeling Push Up",
            "Lat Pulldown (Cable)", "Lat Pulldown - Close Grip (Cable)", "Lateral Raise (Dumbbell)",
            "Overhead Press (Barbell)", "Overhead Press (Dumbbell)", "Overhead Press (Smith Machine)", "Pullover (Dumbbell)",
            "Push Up", "Rear Delt Reverse Fly (Dumbbell)", "Reverse Curl (Barbell)", "Reverse Curl (Dumbbell)",
            "Reverse Grip Lat Pulldown (Cable)", "Running", "Seated Cable Row - Bar Grip", "Seated Cable Row - V Grip (Cable)",
            "Seated Row (Machine)", "Shoulder Press (Dumbbell)", "Skullcrusher (Dumbbell)", "Squat (Barbell)", "Treadmill",
            "Triceps Extension (Cable)", "Triceps Extension (Dumbbell)", "Triceps Rope Pushdown", "Walking",
        )
        val matcher = ExerciseMatcher(library)
        val results = names.associateWith { matcher.match(it) }

        val review = results.filterValues { it.confidence.needsReview }.keys
        assertEquals(setOf("Kneeling Push Up", "Running", "Treadmill", "Walking"), review)
        assertEquals("Barbell Bench Press - Medium Grip", byId[results.getValue("Bench Press (Barbell)").exerciseId]?.name)
        assertEquals("Barbell Squat", byId[results.getValue("Squat (Barbell)").exerciseId]?.name)
        assertEquals("Face Pull", byId[results.getValue("Face Pull").exerciseId]?.name)
        // Cardio still gets suggestions to pick from.
        assertTrue(results.getValue("Treadmill").suggestions.any { byId[it]?.name == "Walking, Treadmill" })
    }

    @Test
    fun wordOrderAndPluralsDoNotMatter() {
        val matcher = ExerciseMatcher(library)
        val match = matcher.match("Deadlift (Barbell)")
        assertEquals(MatchConfidence.EXACT, match.confidence)
        assertEquals("Barbell_Deadlift", match.exerciseId)
        assertEquals("Dumbbell_Flyes", matcher.match("Dumbbell Fly").exerciseId)
    }

    @Test
    fun aSavedChoiceWinsAndUnknownNamesNeedReview() {
        val saved = mapOf(ExerciseMatcher.normalizeName("Kneeling Push Up") to "Pushups")
        val matcher = ExerciseMatcher(library, saved)
        val match = matcher.match("  kneeling push up ")
        assertEquals(MatchConfidence.SAVED, match.confidence)
        assertEquals("Pushups", match.exerciseId)

        val unknown = matcher.match("Zercher Carry Thing")
        assertTrue(unknown.confidence.needsReview)
        assertNull(unknown.exerciseId)
    }
}
