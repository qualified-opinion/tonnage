package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

object Format {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val decimal = DecimalFormat("0.##", symbols)
    private val grouped = DecimalFormat("#,##0", symbols)

    /** 60.0 -> "60", 62.5 -> "62.5", null -> "". */
    fun number(value: Double?): String = value?.let { decimal.format(it) } ?: ""

    fun volume(kg: Double): String = "${grouped.format(kg)} kg"

    /** 1500 -> "1,500". */
    fun count(n: Int): String = grouped.format(n)

    /** Accepts "62.5" or "62,5"; blank or invalid gives null. */
    fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

    fun parseInt(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }

    /**
     * "1:30" or "90" -> 90 seconds; "1:02:03" -> 3723. Plain digits fill from the right like a
     * stopwatch, because the number pad has no colon: "45" is 45 s, "130" is 1:30, "10000" is 1:00:00.
     */
    fun parseDuration(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.length > 2 && trimmed.all { it.isDigit() }) {
            if (trimmed.length > 6) return null
            val padded = trimmed.padStart(6, '0')
            return parseDuration("${padded.substring(0, 2)}:${padded.substring(2, 4)}:${padded.substring(4)}")
        }
        val parts = trimmed.split(':')
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

    /** "Last backup: never", "today", "yesterday" or "N days ago", by calendar day in [zone]. */
    fun lastBackup(lastMs: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (lastMs == null) return "Last backup: never"
        val days = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(lastMs).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(),
        )
        return when {
            days <= 0 -> "Last backup: today"
            days == 1L -> "Last backup: yesterday"
            else -> "Last backup: $days days ago"
        }
    }

    fun restSeconds(seconds: Int): String = if (seconds <= 0) "Off" else duration(seconds * 1000L)

    /** Short form for the "previous" column: "60kg × 8", "12 reps", "2km 10:00", or "" when empty. */
    fun compactSet(set: WorkoutSet, trackingType: TrackingType): String = when (trackingType) {
        TrackingType.WEIGHT_REPS -> when {
            set.weightKg != null && set.reps != null -> "${number(set.weightKg)}kg × ${set.reps}"
            set.weightKg != null -> "${number(set.weightKg)}kg"
            set.reps != null -> "× ${set.reps}"
            else -> ""
        }
        TrackingType.BODYWEIGHT_REPS -> set.reps?.let { "$it reps" } ?: ""
        TrackingType.DURATION -> set.durationSec?.let { duration(it * 1000L) } ?: ""
        TrackingType.DISTANCE_DURATION -> listOfNotNull(
            set.distanceKm?.let { "${number(it)}km" },
            set.durationSec?.let { duration(it * 1000L) },
        ).joinToString(" ")
    }

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
