package com.janreins.tonnage

import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun lastBackupCountsCalendarDays() {
        val zone = java.time.ZoneId.of("Europe/Amsterdam")
        fun at(text: String) = java.time.LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        val now = at("2026-10-17T09:00")
        assertEquals("Last backup: never", Format.lastBackup(null, now, zone))
        assertEquals("Last backup: today", Format.lastBackup(at("2026-10-17T00:05"), now, zone))
        assertEquals("Last backup: yesterday", Format.lastBackup(at("2026-10-16T23:55"), now, zone))
        assertEquals("Last backup: 9 days ago", Format.lastBackup(at("2026-10-08T12:00"), now, zone))
        // A clock that moved backwards still reads as today.
        assertEquals("Last backup: today", Format.lastBackup(at("2026-10-18T08:00"), now, zone))
    }

    @Test
    fun numbersDropTrailingZeros() {
        assertEquals("60", Format.number(60.0))
        assertEquals("62.5", Format.number(62.5))
        assertEquals("", Format.number(null))
    }

    @Test
    fun decimalsAcceptCommaOrDot() {
        assertEquals(62.5, Format.parseDecimal("62,5")!!, 0.0)
        assertEquals(62.5, Format.parseDecimal(" 62.5 ")!!, 0.0)
        assertNull(Format.parseDecimal(""))
        assertNull(Format.parseDecimal("."))
    }

    @Test
    fun durationsFormatAndParse() {
        assertEquals("1:23", Format.duration(83_000))
        assertEquals("1:02:03", Format.duration(3_723_000))
        assertEquals("0:00", Format.duration(0))
        assertEquals(90, Format.parseDuration("1:30"))
        assertEquals(90, Format.parseDuration("90"))
        assertEquals(3723, Format.parseDuration("1:02:03"))
        assertNull(Format.parseDuration("1:"))
        assertNull(Format.parseDuration(""))
    }

    @Test
    fun durationDigitsFillFromTheRight() {
        // The number pad has no colon, so plain digits are read like a stopwatch.
        assertEquals(45, Format.parseDuration("45"))
        assertEquals(90, Format.parseDuration("130"))
        assertEquals(600, Format.parseDuration("1000"))
        assertEquals(3723, Format.parseDuration("10203"))
        assertEquals(3600, Format.parseDuration("10000"))
        assertEquals(90, Format.parseDuration("0130"))
        assertNull(Format.parseDuration("1234567"))
    }

    @Test
    fun restOffAndVolume() {
        assertEquals("Off", Format.restSeconds(0))
        assertEquals("1:30", Format.restSeconds(90))
        assertEquals("12,345 kg", Format.volume(12_345.4))
    }

    @Test
    fun setTargetsByTrackingType() {
        val set = WorkoutSet(workoutExerciseId = 1, position = 0, weightKg = 60.0, reps = 8, durationSec = 90, distanceKm = 5.0)
        assertEquals("60 kg × 8", Format.setTarget(set, TrackingType.WEIGHT_REPS))
        assertEquals("8 reps", Format.setTarget(set, TrackingType.BODYWEIGHT_REPS))
        assertEquals("1:30", Format.setTarget(set, TrackingType.DURATION))
        assertEquals("5 km in 1:30", Format.setTarget(set, TrackingType.DISTANCE_DURATION))
        assertEquals("", Format.setTarget(WorkoutSet(workoutExerciseId = 1, position = 0), TrackingType.WEIGHT_REPS))
    }

    @Test
    fun compactSetsForThePreviousColumn() {
        fun set(kg: Double? = null, reps: Int? = null, km: Double? = null, sec: Int? = null) =
            WorkoutSet(workoutExerciseId = 1, position = 0, weightKg = kg, reps = reps, distanceKm = km, durationSec = sec)
        assertEquals("80kg × 8", Format.compactSet(set(80.0, 8), TrackingType.WEIGHT_REPS))
        assertEquals("62.5kg", Format.compactSet(set(62.5), TrackingType.WEIGHT_REPS))
        assertEquals("× 8", Format.compactSet(set(reps = 8), TrackingType.WEIGHT_REPS))
        assertEquals("", Format.compactSet(set(), TrackingType.WEIGHT_REPS))
        assertEquals("12 reps", Format.compactSet(set(reps = 12), TrackingType.BODYWEIGHT_REPS))
        assertEquals("1:30", Format.compactSet(set(sec = 90), TrackingType.DURATION))
        assertEquals("5km 25:00", Format.compactSet(set(km = 5.0, sec = 1500), TrackingType.DISTANCE_DURATION))
    }
}
