package com.janreins.tonnage

import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.SetInput
import com.janreins.tonnage.domain.SetInput.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetInputTest {

    private fun set(kg: Double? = null, reps: Int? = null, km: Double? = null, sec: Int? = null) =
        WorkoutSet(workoutExerciseId = 1, position = 0, weightKg = kg, reps = reps, distanceKm = km, durationSec = sec)

    @Test
    fun aSetIsFilledByTheValuesItsExerciseLogs() {
        assertFalse(SetInput.isFilled(set(), TrackingType.WEIGHT_REPS))
        assertTrue(SetInput.isFilled(set(kg = 60.0), TrackingType.WEIGHT_REPS))
        assertTrue(SetInput.isFilled(set(reps = 8), TrackingType.WEIGHT_REPS))

        assertFalse(SetInput.isFilled(set(kg = 20.0), TrackingType.BODYWEIGHT_REPS))
        assertTrue(SetInput.isFilled(set(reps = 12), TrackingType.BODYWEIGHT_REPS))

        // A plank logged with reps before it became a timed exercise still counts as blank.
        assertFalse(SetInput.isFilled(set(reps = 3), TrackingType.DURATION))
        assertTrue(SetInput.isFilled(set(sec = 60), TrackingType.DURATION))

        assertFalse(SetInput.isFilled(set(), TrackingType.DISTANCE_DURATION))
        assertTrue(SetInput.isFilled(set(km = 5.0), TrackingType.DISTANCE_DURATION))
        assertTrue(SetInput.isFilled(set(sec = 1800), TrackingType.DISTANCE_DURATION))
    }

    @Test
    fun onlyOneDecimalSeparatorIsAccepted() {
        assertEquals("62.5", SetInput.accept(Kind.WEIGHT, "62", "62.5"))
        assertEquals("62,5", SetInput.accept(Kind.WEIGHT, "62", "62,5"))
        assertNull(SetInput.accept(Kind.WEIGHT, "62.5", "62.5."))
        assertNull(SetInput.accept(Kind.DISTANCE, "5,", "5,."))
        // Anything that isn't part of a number is dropped.
        assertEquals("60", SetInput.accept(Kind.WEIGHT, "6", "6-0"))
        assertEquals("8", SetInput.accept(Kind.REPS, "", "8."))
        assertEquals("1:30", SetInput.accept(Kind.DURATION, "1:3", "1:30"))
    }

    @Test
    fun newValuesAreCapped() {
        assertEquals("1000", SetInput.accept(Kind.WEIGHT, "100", "1000"))
        assertNull(SetInput.accept(Kind.WEIGHT, "1000", "10000"))
        assertNull(SetInput.accept(Kind.WEIGHT, "999", "9999"))
        assertEquals("999", SetInput.accept(Kind.REPS, "99", "999"))
        assertNull(SetInput.accept(Kind.REPS, "999", "9999"))
        assertNull(SetInput.accept(Kind.DISTANCE, "100", "1001"))
        assertEquals("995959", SetInput.accept(Kind.DURATION, "99595", "995959"))
        assertNull(SetInput.accept(Kind.DURATION, "999999", "9999999"))
        assertNull(SetInput.accept(Kind.DURATION, "10:00:00", "100:00:00"))
        // Clearing a cell is always allowed.
        assertEquals("", SetInput.accept(Kind.WEIGHT, "80", ""))
    }

    @Test
    fun aStoredValueAboveTheCapStillShowsAndCanBeShortened() {
        // Values stored before the cap existed (or imported) are displayed as they are...
        assertEquals("1200", Format.number(1200.0))
        // ...and editing them down is allowed, while growing them further is not.
        assertEquals("120", SetInput.accept(Kind.WEIGHT, "1200", "120"))
        assertEquals("1100", SetInput.accept(Kind.WEIGHT, "1200", "1100"))
        assertNull(SetInput.accept(Kind.WEIGHT, "1200", "12000"))
    }

    @Test
    fun aTimeTypedOnTheNumberPadShowsAsMinutesAndSeconds() {
        // What the cell shows after it loses focus: the stored seconds, formatted.
        val stored = Format.parseDuration("130")!!
        assertEquals("1:30", Format.duration(stored * 1000L))
    }
}
