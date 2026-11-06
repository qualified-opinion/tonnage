package com.janreins.tonnage.domain

import com.janreins.tonnage.data.model.BodyWeight
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/** How far back the body weight chart looks. */
enum class WeightRange(val label: String, val months: Long?) {
    MONTHS_3("3 months", 3),
    YEAR("1 year", 12),
    ALL("All", null),
}

/** The entries in a range, the latest one, and the change from the first to the latest (null with one entry). */
data class WeightTrend(val shown: List<BodyWeight>, val latest: BodyWeight, val changeKg: Double?)

object BodyWeights {
    const val MAX_KG = SetInput.MAX_WEIGHT_KG

    /** The entries from [range] before [today] onwards, oldest first; null when there are none. */
    fun trend(entries: List<BodyWeight>, range: WeightRange, today: LocalDate): WeightTrend? {
        val from = range.months?.let { today.minusMonths(it).toEpochDay() } ?: Long.MIN_VALUE
        val shown = entries.filter { it.epochDay >= from }.sortedBy { it.epochDay }
        val latest = shown.lastOrNull() ?: return null
        val change = if (shown.size > 1) latest.weightKg - shown.first().weightKg else null
        return WeightTrend(shown, latest, change)
    }

    /** Only today or earlier can be saved, with a weight above 0 and at most [MAX_KG]. */
    fun canSave(date: LocalDate, weightKg: Double?, today: LocalDate): Boolean =
        !date.isAfter(today) && weightKg != null && weightKg > 0.0 && weightKg <= MAX_KG

    /** "82.4 kg". */
    fun kg(value: Double): String = "${Format.number(round(value))} kg"

    /** "−1.2 kg", "+0.5 kg" or "no change", rounded to 0.1 kg. */
    fun change(value: Double): String {
        val rounded = round(value)
        return when {
            rounded == 0.0 -> "no change"
            rounded < 0 -> "−${Format.number(abs(rounded))} kg"
            else -> "+${Format.number(rounded)} kg"
        }
    }

    private fun round(value: Double): Double = (value * 10).roundToLong() / 10.0
}
