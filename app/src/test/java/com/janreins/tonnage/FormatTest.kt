package com.janreins.tonnage

import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

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
}
