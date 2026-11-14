package com.janreins.tonnage.ui.exercises

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.RecentExercise

/** How many of the user's recently done exercises the list shows first. */
const val RECENT_EXERCISES = 8

/** How many popular exercises the list shows after the recent ones. */
const val POPULAR_EXERCISES = 12

data class ExerciseSections(
    /** Recently done exercises that match, with when each was last done. */
    val recent: List<Pair<Exercise, Long>>,
    val popular: List<Exercise>,
    val rest: List<Exercise>,
)

data class ExerciseFilter(
    val query: String = "",
    val muscle: String? = null,
    val equipment: String? = null,
    val customOnly: Boolean = false,
) {
    val isActive: Boolean get() = muscle != null || equipment != null || customOnly

    /** True when nothing is searched or filtered, so the whole library is listed. */
    val isBrowsing: Boolean get() = query.isBlank() && !isActive

    private val queryWords: List<String>
        get() = query.lowercase().split(' ').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Matches every word of the query against the name, so "inc db press" finds
     * "Incline Dumbbell Press". "db", "bb" and "kb" are treated as equipment shorthands.
     */
    fun matches(exercise: Exercise): Boolean = matches(exercise, queryWords)

    private fun matches(exercise: Exercise, words: List<String>): Boolean =
        (muscle == null || muscle in exercise.primaryMuscleList || muscle in exercise.secondaryMuscleList) &&
            (equipment == null || exercise.equipment == equipment) &&
            (!customOnly || exercise.isCustom) &&
            words.all { word -> matchesWord(exercise, word) }

    /**
     * Every match, A to Z. With a muscle picked, exercises that mainly work it come before
     * ones that only work it on the side; with a query, names starting with it come first.
     */
    fun apply(exercises: List<Exercise>): List<Exercise> {
        val words = queryWords
        val first = words.firstOrNull() ?: ""
        return exercises.filter { matches(it, words) }
            .sortedWith(
                compareBy<Exercise> { !worksMainly(it) }
                    .thenBy { !it.name.lowercase().startsWith(first) }
                    .thenBy { it.name.lowercase() },
            )
    }

    /** The [PopularExercises] that match, most common first (mainly worked muscle first). */
    fun popular(exercises: List<Exercise>): List<Exercise> {
        val byId = exercises.associateBy { it.id }
        val words = queryWords
        return PopularExercises.ids.mapNotNull { byId[it] }
            .filter { matches(it, words) }
            .sortedBy { !worksMainly(it) }
    }

    /**
     * Splits the list into the user's own recent matches, the popular matches, and the rest.
     * While browsing, the rest is the whole library A to Z; while searching or filtering,
     * exercises already shown in the first two sections are left out of it.
     */
    fun sections(exercises: List<Exercise>, recent: List<RecentExercise>): ExerciseSections {
        val byId = exercises.associateBy { it.id }
        val recentShown = recent
            .mapNotNull { r -> byId[r.exerciseId]?.takeIf { e -> matches(e) }?.let { it to r.lastStartedAt } }
            .take(RECENT_EXERCISES)
        val recentIds = recentShown.map { it.first.id }.toSet()
        val popular = popular(exercises).filterNot { it.id in recentIds }.take(POPULAR_EXERCISES)
        val shownIds = recentIds + popular.map { it.id }
        val all = apply(exercises)
        return ExerciseSections(
            recent = recentShown,
            popular = popular,
            rest = if (isBrowsing) all else all.filterNot { it.id in shownIds },
        )
    }

    private fun worksMainly(exercise: Exercise): Boolean = muscle == null || muscle in exercise.primaryMuscleList

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
