package com.janreins.tonnage.data.hevy

import com.janreins.tonnage.data.model.SetType
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One set from a Hevy export. Weights are always in kg here, whatever unit the file used. */
data class HevySet(
    val type: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val distanceKm: Double?,
    val durationSec: Int?,
    val rpe: Double?,
)

data class HevyExercise(val name: String, val notes: String, val sets: List<HevySet>)

data class HevyWorkout(
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val description: String,
    val exercises: List<HevyExercise>,
)

class HevyFormatException(message: String) : Exception(message)

/**
 * Reads Hevy's "Export Workouts" CSV: one row per set, grouped here into workouts (by title and
 * start time) and exercises (consecutive rows with the same exercise title). Accepts kg or lb and
 * km or mile columns, and Hevy's "22 Aug 2026, 09:03" dates as well as ISO dates.
 */
object HevyCsv {

    private const val LB_TO_KG = 0.45359237
    private const val MILE_TO_KM = 1.609344

    fun parse(text: String, timeZone: TimeZone = TimeZone.getDefault()): List<HevyWorkout> {
        val rows = parseRows(text.removePrefix("\uFEFF")).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw HevyFormatException("The file is empty.")
        val header = rows.first().map { it.trim().lowercase(Locale.US) }
        fun column(vararg names: String): Int? = names.firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } }

        val title = column("title") ?: throw notHevy()
        val start = column("start_time") ?: throw notHevy()
        val exercise = column("exercise_title") ?: throw notHevy()
        val end = column("end_time")
        val description = column("description")
        val notes = column("exercise_notes")
        val setType = column("set_type")
        val weightKg = column("weight_kg")
        val weightLb = column("weight_lbs", "weight_lb")
        val reps = column("reps")
        val distanceKm = column("distance_km")
        val distanceMiles = column("distance_miles", "distance_mi")
        val duration = column("duration_seconds")
        val rpe = column("rpe")

        // Hevy lists newest first; keep the file's grouping but return oldest first.
        val workouts = LinkedHashMap<Pair<String, String>, MutableList<List<String>>>()
        rows.drop(1).forEach { row ->
            fun cell(index: Int?) = index?.let { row.getOrNull(it) }?.trim().orEmpty()
            workouts.getOrPut(cell(title) to cell(start)) { mutableListOf() } += row
        }
        return workouts.map { (key, workoutRows) ->
            fun List<String>.cell(index: Int?) = index?.let { getOrNull(it) }?.trim().orEmpty()
            val first = workoutRows.first()
            val startedAt = parseDate(key.second, timeZone)
                ?: throw HevyFormatException("Couldn't read the date \"${key.second}\" for \"${key.first}\".")
            val endedAt = parseDate(first.cell(end), timeZone)?.takeIf { it >= startedAt } ?: startedAt

            val exercises = mutableListOf<HevyExercise>()
            var currentName: String? = null
            var currentNotes = ""
            var currentSets = mutableListOf<HevySet>()
            fun flush() {
                currentName?.let { exercises += HevyExercise(it, currentNotes, currentSets) }
            }
            workoutRows.forEach { row ->
                val name = row.cell(exercise)
                if (name.isEmpty()) return@forEach
                if (name != currentName) {
                    flush()
                    currentName = name
                    currentNotes = row.cell(notes)
                    currentSets = mutableListOf()
                }
                currentSets += HevySet(
                    type = setTypeOf(row.cell(setType)),
                    weightKg = number(row.cell(weightKg)) ?: number(row.cell(weightLb))?.times(LB_TO_KG)?.let(::roundKg),
                    reps = number(row.cell(reps))?.toInt(),
                    distanceKm = number(row.cell(distanceKm)) ?: number(row.cell(distanceMiles))?.times(MILE_TO_KM),
                    durationSec = number(row.cell(duration))?.toInt(),
                    rpe = number(row.cell(rpe)),
                )
            }
            flush()
            HevyWorkout(
                title = key.first.ifEmpty { "Workout" },
                startedAt = startedAt,
                endedAt = endedAt,
                description = first.cell(description),
                exercises = exercises,
            )
        }.sortedBy { it.startedAt }
    }

    private fun notHevy() = HevyFormatException(
        "This doesn't look like a Hevy workout export (it needs title, start_time and exercise_title columns).",
    )

    private fun setTypeOf(value: String): SetType = when (value.lowercase(Locale.US)) {
        "warmup", "warm_up", "warm-up" -> SetType.WARMUP
        "dropset", "drop_set", "drop" -> SetType.DROP
        "failure" -> SetType.FAILURE
        else -> SetType.NORMAL
    }

    private fun number(value: String): Double? = value.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

    /** Pounds converted to kg are kept to 0.01 kg so 135 lb doesn't become 61.23496995 kg. */
    private fun roundKg(kg: Double): Double = Math.round(kg * 100) / 100.0

    private val datePatterns = listOf("d MMM yyyy, HH:mm", "d MMM yyyy, HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss")

    fun parseDate(value: String, timeZone: TimeZone = TimeZone.getDefault()): Long? {
        if (value.isBlank()) return null
        for (pattern in datePatterns) {
            val format = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                this.timeZone = timeZone
                isLenient = false
            }
            val position = ParsePosition(0)
            val date = format.parse(value.trim(), position)
            if (date != null && position.index == value.trim().length) return date.time
        }
        return null
    }

    /** RFC 4180 CSV: quoted fields may contain commas, doubled quotes and line breaks. */
    fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    rows += row; row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }
}
