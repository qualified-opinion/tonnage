package com.janreins.tonnage

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.RecentExercise
import com.janreins.tonnage.ui.exercises.ExerciseFilter
import com.janreins.tonnage.ui.exercises.POPULAR_EXERCISES
import com.janreins.tonnage.ui.exercises.PopularExercises
import com.janreins.tonnage.ui.exercises.RECENT_EXERCISES
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseFilterTest {

    private val bench = exercise("Barbell_Bench_Press_-_Medium_Grip", "Barbell Bench Press - Medium Grip", "barbell", "chest", "triceps,shoulders")
    private val incline = exercise("Incline_Dumbbell_Press", "Incline Dumbbell Press", "dumbbell", "chest", "shoulders")
    private val curl = exercise("Dumbbell_Bicep_Curl", "Dumbbell Bicep Curl", "dumbbell", "biceps")
    private val custom = exercise("custom-1", "Landmine Press", "other", "shoulders", isCustom = true)
    private val all = listOf(bench, incline, curl, custom)

    @Test
    fun emptyFilterReturnsEverythingSortedByName() {
        assertEquals(listOf(bench, curl, incline, custom), ExerciseFilter().apply(all))
    }

    @Test
    fun everyQueryWordMustMatch() {
        assertEquals(listOf(incline), ExerciseFilter(query = "inc press").apply(all))
    }

    @Test
    fun equipmentShorthandsExpand() {
        assertEquals(listOf(curl, incline), ExerciseFilter(query = "db").apply(all))
    }

    @Test
    fun muscleFilterIncludesSecondaryMusclesAfterMainOnes() {
        assertEquals(listOf(custom, bench, incline), ExerciseFilter(muscle = "shoulders").apply(all))
    }

    @Test
    fun equipmentAndCustomFiltersCombine() {
        assertEquals(listOf(curl, incline), ExerciseFilter(equipment = "dumbbell").apply(all))
        assertEquals(listOf(custom), ExerciseFilter(customOnly = true).apply(all))
    }

    @Test
    fun namesStartingWithQueryComeFirst() {
        assertEquals(listOf(curl, incline), ExerciseFilter(query = "dumbbell").apply(all))
    }

    @Test
    fun popularExercisesComeInCommonOrder() {
        // Library order is alphabetical; popular order puts the barbell bench press first.
        assertEquals(listOf(bench, incline, curl), ExerciseFilter().popular(all))
        assertEquals(listOf(bench, incline), ExerciseFilter(query = "press").popular(all))
        assertEquals(emptyList<Exercise>(), ExerciseFilter(customOnly = true).popular(all))
    }

    @Test
    fun popularForAMusclePutsExercisesThatMainlyWorkItFirst() {
        val press = exercise("Standing_Military_Press", "Standing Military Press", "barbell", "shoulders", "triceps")
        val library = all + press
        assertEquals(listOf(press, bench, incline), ExerciseFilter(muscle = "shoulders").popular(library))
    }

    @Test
    fun browsingShowsRecentThenPopularThenTheWholeLibrary() {
        val sections = ExerciseFilter().sections(all, listOf(RecentExercise(incline.id, 5L), RecentExercise("gone", 4L)))
        assertEquals(listOf(incline to 5L), sections.recent)
        assertEquals(listOf(bench, curl), sections.popular)
        assertEquals(listOf(bench, curl, incline, custom), sections.rest)
    }

    @Test
    fun filteringShowsEachMatchOnce() {
        val sections = ExerciseFilter(muscle = "chest").sections(all, listOf(RecentExercise("custom-1", 9L), RecentExercise(incline.id, 5L)))
        assertEquals(listOf(incline to 5L), sections.recent)
        assertEquals(listOf(bench), sections.popular)
        assertEquals(emptyList<Exercise>(), sections.rest)
    }

    @Test
    fun recentAndPopularAreCapped() {
        val many = (1..30).map { exercise(PopularExercises.ids[it], "Lift $it", "barbell", "chest") }
        val recent = many.drop(15).mapIndexed { i, e -> RecentExercise(e.id, 100L - i) }
        val sections = ExerciseFilter().sections(many, recent)
        assertEquals(RECENT_EXERCISES, sections.recent.size)
        assertEquals(many.take(POPULAR_EXERCISES), sections.popular)
    }

    private fun exercise(
        id: String,
        name: String,
        equipment: String,
        primary: String,
        secondary: String = "",
        isCustom: Boolean = false,
    ) = Exercise(
        id = id,
        name = name,
        category = "strength",
        equipment = equipment,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        isCustom = isCustom,
    )
}
