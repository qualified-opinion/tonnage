package com.janreins.tonnage

import com.janreins.tonnage.timer.RestTimer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RestTimerTest {
    private var now = 1_000_000L

    @Before
    fun setUp() {
        RestTimer.clock = { now }
        RestTimer.skip()
    }

    @After
    fun tearDown() = RestTimer.skip()

    @Test
    fun countsDownFromStart() {
        RestTimer.start(90, "Bench Press", "Set 2")
        now += 30_000
        assertEquals(60_000, RestTimer.remainingMs(RestTimer.state.value!!))
    }

    @Test
    fun addingAndRemovingTime() {
        RestTimer.start(60, "Bench Press", null)
        RestTimer.adjust(15)
        assertEquals(75_000, RestTimer.remainingMs(RestTimer.state.value!!))
        RestTimer.adjust(-15)
        RestTimer.adjust(-15)
        assertEquals(45_000, RestTimer.remainingMs(RestTimer.state.value!!))
        RestTimer.adjust(-60)
        assertNull(RestTimer.state.value)
    }

    @Test
    fun zeroSecondsMeansNoTimer() {
        RestTimer.start(0, "Bench Press", null)
        assertNull(RestTimer.state.value)
    }

    @Test
    fun finishOnlyEndsTheSameCountdown() {
        RestTimer.start(60, "Bench Press", null)
        val first = RestTimer.state.value!!
        RestTimer.start(90, "Squat", null)
        RestTimer.finish(first)
        assertNotNull(RestTimer.state.value)
        RestTimer.finish(RestTimer.state.value!!)
        assertNull(RestTimer.state.value)
    }
}
