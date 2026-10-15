package com.janreins.tonnage.data.model

/** Muscle and equipment names used by the bundled library, in display order. */
object ExerciseOptions {
    val muscles = listOf(
        "chest", "shoulders", "triceps", "biceps", "forearms", "lats", "middle back", "lower back",
        "traps", "neck", "abdominals", "quadriceps", "hamstrings", "glutes", "calves", "adductors",
        "abductors",
    )

    val equipment = listOf(
        "barbell", "dumbbell", "machine", "cable", "body only", "kettlebells", "bands",
        "e-z curl bar", "medicine ball", "exercise ball", "foam roll", "other", "none",
    )

    fun display(value: String): String = value.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}
