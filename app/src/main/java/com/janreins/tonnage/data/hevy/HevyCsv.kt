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

/** The same normalised identity is used by parsing, preview and transactional insertion. */
data class HevyWorkoutIdentity(val title: String, val startedAt: Long) {
    companion object {
        fun of(title: String, startedAt: Long) =
            HevyWorkoutIdentity(title.trim().ifEmpty { "Workout" }, startedAt)
    }
}

val HevyWorkout.identity: HevyWorkoutIdentity get() = HevyWorkoutIdentity.of(title, startedAt)

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
        val rows = detailedRows(text.removePrefix("\uFEFF")).filter { row -> row.cells.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw HevyFormatException("The file is empty.")
        val header = rows.first().cells.map { it.trim().lowercase(Locale.US) }
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
        val workouts = LinkedHashMap<HevyWorkoutIdentity, MutableList<CsvRow>>()
        val startTimes = mutableMapOf<String, Long>()
        fun CsvRow.finite(value: Double, index: Int): Double {
            if (!value.isFinite()) throw HevyFormatException("Row $line has an invalid value in ${header[index]}.")
            return value
        }
        fun CsvRow.number(index: Int?, integer: Boolean = false): Double? {
            val value = cell(index).replace(',', '.').toDoubleOrNull() ?: return null
            if (!value.isFinite() || (integer && value > Int.MAX_VALUE)) {
                throw HevyFormatException("Row $line has an invalid value in ${header[index!!]}.")
            }
            return value.takeIf { it >= 0.0 }
        }
        rows.drop(1).forEach { row ->
            // Validate even unused fallback columns and rows without an exercise name.
            listOf(weightKg, weightLb, distanceKm, distanceMiles, rpe).forEach { row.number(it) }
            listOf(reps, duration).forEach { row.number(it, integer = true) }
            val date = row.cell(start)
            val startedAt = startTimes.getOrPut(date) {
                parseDate(date, timeZone)
                    ?: throw HevyFormatException("Couldn't read the date \"$date\" for \"${row.cell(title)}\".")
            }
            workouts.getOrPut(HevyWorkoutIdentity.of(row.cell(title), startedAt)) { mutableListOf() } += row
        }
        return workouts.map { (key, workoutRows) ->
            val first = workoutRows.first()
            val startedAt = key.startedAt
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
                    weightKg = row.number(weightKg) ?: row.number(weightLb)?.let {
                        val kg = row.finite(it * LB_TO_KG, weightLb!!)
                        row.finite(roundKg(kg), weightLb)
                    },
                    reps = row.number(reps, integer = true)?.toInt(),
                    distanceKm = row.number(distanceKm) ?: row.number(distanceMiles)?.let {
                        row.finite(it * MILE_TO_KM, distanceMiles!!)
                    },
                    durationSec = row.number(duration, integer = true)?.toInt(),
                    rpe = row.number(rpe),
                )
            }
            flush()
            HevyWorkout(
                title = key.title,
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

    /** Pounds converted to kg are kept to 0.01 kg so 135 lb doesn't become 61.23496995 kg. */
    private fun roundKg(kg: Double): Double = Math.floor(kg * 100 + 0.5) / 100.0

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
    fun parseRows(text: String): List<List<String>> = detailedRows(text.removePrefix("\uFEFF")).map { it.cells }

    private data class CsvRow(val line: Int, val cells: List<String>) {
        fun cell(index: Int?): String = index?.let { cells.getOrNull(it) }?.trim().orEmpty()
    }

    private fun detailedRows(text: String): List<CsvRow> {
        val rows = mutableListOf<CsvRow>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var line = 1
        var rowLine = 1
        var quoteLine = 1
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> {
                    if (!quoted) quoteLine = line
                    quoted = !quoted
                }
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    rows += CsvRow(rowLine, row); row = mutableListOf()
                    rowLine = line + 1
                }
                else -> field.append(c)
            }
            if (c == '\n' || (c == '\r' && (!quoted || text.getOrNull(i + 1) != '\n'))) line++
            i++
        }
        if (quoted) throw HevyFormatException("Row $quoteLine has an unclosed quote.")
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += CsvRow(rowLine, row)
        }
        return rows
    }
}
