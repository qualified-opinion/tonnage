package com.janreins.tonnage.data.backup

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.WorkoutStats
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Finished workouts as a spreadsheet: one row per set, oldest workout first. */
object WorkoutCsv {
    val HEADER = listOf(
        "date", "start_time", "end_time", "workout", "exercise", "set_number", "set_type",
        "weight_kg", "reps", "distance_km", "duration_seconds", "volume_kg",
    )

    private const val BOM = "﻿"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun write(contents: BackupContents, library: Map<String, Exercise>, zone: ZoneId): String {
        val rows = mutableListOf(HEADER)
        contents.workouts.filter { it.workout.endedAt != null }.sortedBy { it.workout.startedAt }.forEach { (workout, exercises) ->
            val start = Instant.ofEpochMilli(workout.startedAt).atZone(zone)
            val end = workout.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
            exercises.sortedBy { it.first.position }.forEach { (workoutExercise, sets) ->
                val exercise = library[workoutExercise.exerciseId]
                sets.sortedBy { it.position }.forEachIndexed { index, set ->
                    rows += listOf(
                        start.format(dateFormat),
                        start.format(timeFormat),
                        end?.format(timeFormat).orEmpty(),
                        text(workout.title),
                        text(exercise?.name ?: "Unknown exercise"),
                        (index + 1).toString(),
                        text(set.type.label),
                    ) + values(set, exercise?.trackingType)
                }
            }
        }
        return BOM + rows.joinToString("") { row -> row.joinToString(",") { quote(it) } + "\r\n" }
    }

    /** weight_kg, reps, distance_km, duration_seconds, volume_kg: only what this way of logging uses. */
    private fun values(set: WorkoutSet, type: TrackingType?): List<String> {
        val weight = type == null || type == TrackingType.WEIGHT_REPS
        val reps = type == null || type == TrackingType.WEIGHT_REPS || type == TrackingType.BODYWEIGHT_REPS
        val distance = type == null || type == TrackingType.DISTANCE_DURATION
        val duration = type == null || type == TrackingType.DURATION || type == TrackingType.DISTANCE_DURATION
        val volume = type == TrackingType.WEIGHT_REPS && set.type != SetType.WARMUP && set.weightKg != null && set.reps != null
        return listOf(
            if (weight) number(set.weightKg, 3) else "",
            if (reps) set.reps?.toString().orEmpty() else "",
            if (distance) number(set.distanceKm, 3) else "",
            if (duration) set.durationSec?.toString().orEmpty() else "",
            if (volume) number(WorkoutStats.setVolume(set), 2) else "",
        )
    }

    /** A dot as the decimal point and no thousands separator, whatever the phone's language. */
    private fun number(value: Double?, decimals: Int): String = value?.let {
        BigDecimal.valueOf(it).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }.orEmpty()

    /** Text a spreadsheet could take for a formula gets a leading quote, so it shows as typed. */
    private fun text(value: String): String =
        if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value

    /** RFC 4180: quote fields with commas, quotes or line breaks, doubling any quote. */
    private fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
