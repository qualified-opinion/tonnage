package com.janreins.tonnage.data.hevy

import com.janreins.tonnage.data.model.Exercise
import java.util.Locale

/** How sure the matcher is about a Hevy name. */
enum class MatchConfidence(val needsReview: Boolean) {
    /** You picked it in an earlier import. */
    SAVED(false),
    /** Same words as a library exercise, in any order. */
    EXACT(false),
    /** A known Hevy name for a library exercise. */
    ALIAS(false),
    /** Nearly the same words. */
    CLOSE(false),
    /** Something similar exists, but it needs a look. */
    LOW(true),
    /** Nothing similar. */
    NONE(true),
}

data class ExerciseMatch(
    val hevyName: String,
    val exerciseId: String?,
    val confidence: MatchConfidence,
    /** Best library candidates, most likely first (for the review screen). */
    val suggestions: List<String>,
)

/**
 * Matches Hevy exercise names to the library: a saved choice first, then the same words in
 * any order ("Bench Press (Barbell)" = "Barbell Bench Press"), then known Hevy aliases, then a
 * word-overlap score. Only low scores go to the review screen.
 */
class ExerciseMatcher(
    library: List<Exercise>,
    private val saved: Map<String, String> = emptyMap(),
) {
    private val byId = library.associateBy { it.id }
    private val entries = library.map { it to tokens(it.name) }
    private val byKey: Map<String, Exercise> = entries
        .groupBy { (_, words) -> key(words) }
        .mapValues { (_, matches) -> matches.minBy { (exercise, _) -> if (exercise.isCustom) 0 else 1 }.first }
    private val aliases: Map<String, String> = HevyAliases.byHevyName
        .mapKeys { (name, _) -> key(tokens(name)) }
        .filterValues { it in byId }

    fun match(hevyName: String): ExerciseMatch {
        saved[normalizeName(hevyName)]?.takeIf { it in byId }?.let {
            return ExerciseMatch(hevyName, it, MatchConfidence.SAVED, emptyList())
        }
        val words = tokens(hevyName)
        val wordKey = key(words)
        byKey[wordKey]?.let { return ExerciseMatch(hevyName, it.id, MatchConfidence.EXACT, emptyList()) }
        aliases[wordKey]?.let { return ExerciseMatch(hevyName, it, MatchConfidence.ALIAS, emptyList()) }

        val equipment = equipmentOf(hevyName)
        val scored = entries
            .map { (exercise, libraryWords) -> exercise to score(words, equipment, exercise, libraryWords) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull()
        val suggestions = scored.take(5).map { it.first.id }
        return when {
            best == null || best.second < LOW_SCORE -> ExerciseMatch(hevyName, null, MatchConfidence.NONE, suggestions)
            best.second >= CLOSE_SCORE && (scored.getOrNull(1)?.second ?: 0.0) < best.second ->
                ExerciseMatch(hevyName, best.first.id, MatchConfidence.CLOSE, suggestions)
            else -> ExerciseMatch(hevyName, null, MatchConfidence.LOW, suggestions)
        }
    }

    private fun score(words: List<String>, equipment: String?, exercise: Exercise, libraryWords: List<String>): Double {
        val a = words.toSet()
        val b = libraryWords.toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var dice = 2.0 * a.intersect(b).size / (a.size + b.size)
        // "(Dumbbell)" in Hevy and a barbell exercise in the library are different exercises.
        val libraryEquipment = normalizeEquipment(exercise.equipment)
        if (equipment != null && libraryEquipment != null && equipment != libraryEquipment) dice *= 0.6
        return dice
    }

    companion object {
        const val CLOSE_SCORE = 0.9
        const val LOW_SCORE = 0.45

        private val stopWords = setOf("the", "a", "an", "with", "on", "of", "to", "and")

        private val synonyms = mapOf(
            "db" to "dumbbell", "bb" to "barbell", "kb" to "kettlebell",
            "pushup" to "push up", "pullup" to "pull up", "chinup" to "chin up", "situp" to "sit up",
            "skullcrusher" to "skull crusher",
            "flye" to "fly", "flys" to "fly", "flyes" to "fly", "flies" to "fly",
            "biceps" to "bicep", "triceps" to "tricep",
            "plates" to "",
        )

        /** Lowercase words with plurals, hyphens and common spellings folded. */
        fun tokens(name: String): List<String> = name.lowercase(Locale.US)
            .replace("e-z", "ez")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
            .flatMap { word -> (synonyms[word] ?: word).split(' ') }
            .map(::singular)
            .filter { it.isNotEmpty() && it !in stopWords }

        private fun singular(word: String): String = when {
            word.length <= 3 || word.endsWith("ss") || word.endsWith("us") -> word
            word.endsWith("ies") -> word.dropLast(3) + "y"
            word.endsWith("s") -> word.dropLast(1)
            else -> word
        }

        private fun key(words: List<String>): String = words.sorted().joinToString(" ")

        /** How a Hevy name is stored for remembered choices: case and spacing don't matter. */
        fun normalizeName(name: String): String = name.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")

        /** Hevy puts the equipment in brackets: "Bench Press (Barbell)". */
        private fun equipmentOf(hevyName: String): String? =
            Regex("\\(([^)]*)\\)\\s*$").find(hevyName)?.groupValues?.get(1)?.let(::normalizeEquipment)

        private fun normalizeEquipment(value: String): String? {
            val v = value.lowercase(Locale.US)
            return when {
                "smith" in v || "machine" in v || "lever" in v -> "machine"
                "dumbbell" in v -> "dumbbell"
                "barbell" in v || "ez" in v || "e-z" in v -> "barbell"
                "cable" in v -> "cable"
                "kettlebell" in v -> "kettlebell"
                "band" in v -> "band"
                "body" in v || "weighted" in v -> "body"
                else -> null
            }
        }
    }
}
