package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet

/** Whether a set has anything logged in it, and what a set cell accepts while typing. */
object SetInput {
    const val MAX_WEIGHT_KG = 1000.0
    const val MAX_DISTANCE_KM = 1000.0
    const val MAX_REPS = 999
    const val MAX_DURATION_SECONDS = 99 * 3600 + 59 * 60 + 59
    private const val MAX_LENGTH = 8

    /** A set is filled when any value its exercise logs is entered; otherwise it is blank. */
    fun isFilled(set: WorkoutSet, type: TrackingType): Boolean = when (type) {
        TrackingType.WEIGHT_REPS -> set.weightKg != null || set.reps != null
        TrackingType.BODYWEIGHT_REPS -> set.reps != null
        TrackingType.DURATION -> set.durationSec != null
        TrackingType.DISTANCE_DURATION -> set.distanceKm != null || set.durationSec != null
    }

    enum class Kind { WEIGHT, REPS, DISTANCE, DURATION }

    /**
     * The text a cell keeps when [input] replaces [current], or null to keep [current]. Only
     * digits, one decimal separator (weight and distance) or colons (time) are kept. A value over
     * its cap is refused unless it is no larger than [current], so a stored value above the cap
     * still shows and can be shortened.
     */
    fun accept(kind: Kind, current: String, input: String): String? {
        val normalized = buildString {
            input.codePoints().forEach { codePoint ->
                val digit = Character.digit(codePoint, 10)
                if (digit >= 0) append('0' + digit) else appendCodePoint(codePoint)
            }
        }
        val filtered = normalized.filter { char ->
            char.isDigit() || when (kind) {
                Kind.WEIGHT, Kind.DISTANCE -> char == '.' || char == ','
                Kind.DURATION -> char == ':'
                Kind.REPS -> false
            }
        }
        if (kind == Kind.DURATION && filtered.length > MAX_LENGTH) return null
        val text = filtered.take(MAX_LENGTH)
        if (kind == Kind.DURATION && input.isNotEmpty()) {
            val parts = text.split(':')
            if (parts.size > 3 || parts.any { it.isEmpty() }) return null
            if (parts.size > 1 && parts.drop(1).any { it.length > 2 || it.toInt() > 59 }) return null
            if (Format.parseDuration(text) == null) return null
        }
        if (text.count { it == '.' || it == ',' } > 1) return null
        // Plain digits for a time fill h:mm:ss from the right, so a seventh digit has nowhere to go.
        if (kind == Kind.DURATION && text.length > 6 && text.all { it.isDigit() }) return null
        val value = valueOf(kind, text) ?: return text
        if (value <= cap(kind)) return text
        val before = valueOf(kind, current)
        return if (before != null && value <= before) text else null
    }

    private fun cap(kind: Kind): Double = when (kind) {
        Kind.WEIGHT -> MAX_WEIGHT_KG
        Kind.DISTANCE -> MAX_DISTANCE_KM
        Kind.REPS -> MAX_REPS.toDouble()
        Kind.DURATION -> MAX_DURATION_SECONDS.toDouble()
    }

    private fun valueOf(kind: Kind, text: String): Double? = when (kind) {
        Kind.WEIGHT, Kind.DISTANCE -> Format.parseDecimal(text)
        Kind.REPS -> Format.parseInt(text)?.toDouble()
        Kind.DURATION -> Format.parseDuration(text)?.toDouble()
    }
}
