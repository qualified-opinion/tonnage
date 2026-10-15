package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Format {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val decimal = DecimalFormat("0.##", symbols)
    private val grouped = DecimalFormat("#,##0", symbols)

    /** 60.0 -> "60", 62.5 -> "62.5", null -> "". */
    fun number(value: Double?): String = value?.let { decimal.format(it) } ?: ""

    fun volume(kg: Double): String = "${grouped.format(kg)} kg"

    /** Accepts "62.5" or "62,5"; blank or invalid gives null. */
    fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

    fun parseInt(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }

    /** "1:30" or "90" -> 90 seconds; "1:02:03" -> 3723. */
    fun parseDuration(text: String): Int? {
        val parts = text.trim().split(':')
        if (parts.any { it.isEmpty() && parts.size > 1 }) return null
        val numbers = parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        return when (numbers.size) {
            1 -> numbers[0]
            2 -> numbers[0] * 60 + numbers[1]
            3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            else -> null
        }
    }

    /** 83_000 -> "1:23", 3_723_000 -> "1:02:03". */
    fun duration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        } else {
            "%d:%02d".format(Locale.US, minutes, seconds)
        }
    }

    fun restSeconds(seconds: Int): String = if (seconds <= 0) "Off" else duration(seconds * 1000L)

    /** "60 kg × 8", "12 reps", "1:30" or "" when nothing is filled in. */
    fun setTarget(set: WorkoutSet, trackingType: TrackingType): String = when (trackingType) {
        TrackingType.WEIGHT_REPS -> when {
            set.weightKg != null && set.reps != null -> "${number(set.weightKg)} kg × ${set.reps}"
            set.weightKg != null -> "${number(set.weightKg)} kg"
            set.reps != null -> "${set.reps} reps"
            else -> ""
        }
        TrackingType.BODYWEIGHT_REPS -> set.reps?.let { "$it reps" } ?: ""
        TrackingType.DURATION -> set.durationSec?.let { duration(it * 1000L) } ?: ""
        TrackingType.DISTANCE_DURATION -> listOfNotNull(
            set.distanceKm?.let { "${number(it)} km" },
            set.durationSec?.let { duration(it * 1000L) },
        ).joinToString(" in ")
    }
}
