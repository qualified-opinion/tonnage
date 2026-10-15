package com.janreins.tonnage

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.ui.exercises.ExerciseFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseFilterTest {

    private val bench = exercise("bench", "Barbell Bench Press - Medium Grip", "barbell", "chest", "triceps,shoulders")
    private val incline = exercise("incline", "Incline Dumbbell Press", "dumbbell", "chest", "shoulders")
    private val curl = exercise("curl", "Dumbbell Bicep Curl", "dumbbell", "biceps")
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
    fun muscleFilterIncludesSecondaryMuscles() {
        assertEquals(listOf(bench, incline, custom), ExerciseFilter(muscle = "shoulders").apply(all))
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
