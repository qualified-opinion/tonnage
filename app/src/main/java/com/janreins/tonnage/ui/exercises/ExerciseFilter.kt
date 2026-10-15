package com.janreins.tonnage.ui.exercises

import com.janreins.tonnage.data.model.Exercise

data class ExerciseFilter(
    val query: String = "",
    val muscle: String? = null,
    val equipment: String? = null,
    val customOnly: Boolean = false,
) {
    val isActive: Boolean get() = muscle != null || equipment != null || customOnly

    /**
     * Matches every word of the query against the name, so "inc db press" finds
     * "Incline Dumbbell Press". "db", "bb" and "kb" are treated as equipment shorthands.
     */
    fun apply(exercises: List<Exercise>): List<Exercise> {
        val words = query.lowercase().split(' ').map { it.trim() }.filter { it.isNotEmpty() }
        return exercises.filter { exercise ->
            (muscle == null || muscle in exercise.primaryMuscleList || muscle in exercise.secondaryMuscleList) &&
                (equipment == null || exercise.equipment == equipment) &&
                (!customOnly || exercise.isCustom) &&
                words.all { word -> matchesWord(exercise, word) }
        }.sortedWith(compareBy<Exercise> { !it.name.lowercase().startsWith(words.firstOrNull() ?: "") }
            .thenBy { it.name.lowercase() })
    }

    private fun matchesWord(exercise: Exercise, word: String): Boolean {
        val name = exercise.name.lowercase()
        val expanded = SHORTHANDS[word]
        return name.contains(word) ||
            (expanded != null && (name.contains(expanded) || exercise.equipment.contains(expanded)))
    }

    private companion object {
        val SHORTHANDS = mapOf(
            "db" to "dumbbell",
            "bb" to "barbell",
            "kb" to "kettlebell",
            "ohp" to "overhead press",
            "rdl" to "romanian deadlift",
        )
    }
}
