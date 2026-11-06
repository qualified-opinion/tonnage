package com.janreins.tonnage

import com.janreins.tonnage.data.model.BodyWeight
import com.janreins.tonnage.domain.BodyWeights
import com.janreins.tonnage.domain.WeightRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BodyWeightsTest {

    private val today = LocalDate.parse("2026-10-26")

    private fun entry(date: String, kg: Double) = BodyWeight(epochDay = LocalDate.parse(date).toEpochDay(), weightKg = kg, createdAt = 0)

    @Test
    fun changeOverThreeMonthsIgnoresEntriesBeforeTheRange() {
        val entries = listOf(entry("2026-10-20", 82.4), entry("2026-05-01", 90.0), entry("2026-07-26", 83.6), entry("2026-09-01", 83.0))
        val trend = BodyWeights.trend(entries, WeightRange.MONTHS_3, today)!!
        assertEquals(listOf("2026-07-26", "2026-09-01", "2026-10-20"), trend.shown.map { it.date.toString() })
        assertEquals(82.4, trend.latest.weightKg, 0.0)
        assertEquals("−1.2 kg", BodyWeights.change(trend.changeKg!!))
        // All of it goes back to the first entry.
        assertEquals("−7.6 kg", BodyWeights.change(BodyWeights.trend(entries, WeightRange.ALL, today)!!.changeKg!!))
    }

    @Test
    fun oneEntryHasNoChange() {
        val trend = BodyWeights.trend(listOf(entry("2026-10-20", 82.4)), WeightRange.YEAR, today)!!
        assertNull(trend.changeKg)
        assertEquals("82.4 kg", BodyWeights.kg(trend.latest.weightKg))
    }

    @Test
    fun emptyOrOutOfRangeReturnsNothing() {
        assertNull(BodyWeights.trend(emptyList(), WeightRange.ALL, today))
        assertNull(BodyWeights.trend(listOf(entry("2025-01-01", 80.0)), WeightRange.MONTHS_3, today))
    }

    @Test
    fun futureDatesAndImpossibleWeightsCantBeSaved() {
        assertTrue(BodyWeights.canSave(today, 82.4, today))
        assertTrue(BodyWeights.canSave(today.minusDays(7), 82.4, today))
        assertFalse(BodyWeights.canSave(today.plusDays(1), 82.4, today))
        assertFalse(BodyWeights.canSave(today, 0.0, today))
        assertFalse(BodyWeights.canSave(today, null, today))
        assertFalse(BodyWeights.canSave(today, 1000.5, today))
    }

    @Test
    fun changeReadsPlainly() {
        assertEquals("+0.5 kg", BodyWeights.change(0.46))
        assertEquals("no change", BodyWeights.change(0.04))
    }
}
