package com.janreins.tonnage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.timer.RestTimer
import com.janreins.tonnage.timer.RestTimerStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RestTimerStoreTest {
    private lateinit var app: Application
    private var elapsed = 1_000_000L
    private var wall = 2_000_000L
    private val originalClock = RestTimer.clock
    private val rest get() = RestTimer.State(elapsed + 60_000, 60_000, "Bench", "Set 2")
    private val prefs get() = app.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)
    private fun store() = RestTimerStore(prefs, { elapsed }, { wall })

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        prefs.edit().clear().commit()
        RestTimer.attach(app)
        RestTimer.skip()
        RestTimer.clock = { elapsed }
    }

    @After fun tearDown() {
        RestTimer.skip()
        RestTimer.clock = originalClock
    }

    @Test fun aNewStoreRecoversFortySecondsAfterTwentySecondsAway() {
        val saved = rest
        store().save(saved)
        elapsed += 20_000; wall += 20_000
        val recovered = store().restore()!!
        assertEquals(saved, recovered)
        assertEquals(40_000L, RestTimer.remainingMs(recovered, elapsed))
    }

    @Test fun anExpiredRestIsRemoved() {
        store().save(rest)
        elapsed += 60_001; wall += 60_001
        assertNull(store().restore())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun rebootWithASmallerElapsedClockCannotReviveARest() {
        store().save(rest)
        elapsed = 1000; wall += 20_000
        assertNull(store().restore())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun clockDisagreementBeyondThreeSecondsIsRemoved() {
        store().save(rest)
        elapsed += 20_000; wall += 24_000
        assertNull(store().restore())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun smallClockDifferencesAreAllowedButRemainingCannotExceedTotal() {
        store().save(rest)
        elapsed += 20_000; wall += 22_000
        assertNotNull(store().restore())
        prefs.edit().putLong("total", 1000).commit()
        assertNull(store().restore())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun restoreDropsATimerThatExpiredWhileResumeWasPending() {
        RestTimer.start(1, "Bench", null)
        elapsed += 1000
        assertNull(RestTimer.restore())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun startAdjustSkipAndFinishKeepTheCheckpointInStep() {
        RestTimer.start(60, "Bench", "Set 2")
        val first = RestTimer.state.value!!
        assertEquals(first.endsAt, prefs.getLong("end", 0))
        RestTimer.adjust(15)
        assertEquals(75_000L, prefs.getLong("total", 0))
        assertEquals(first.endsAt + 15_000, prefs.getLong("end", 0))
        assertFalse(RestTimer.finish(first))
        assertFalse(prefs.all.isEmpty()) // A stale completion cannot erase the adjusted rest.
        assertTrue(RestTimer.finish(RestTimer.state.value!!))
        assertTrue(prefs.all.isEmpty())
        RestTimer.start(60, "Squat", null)
        RestTimer.skip()
        assertTrue(prefs.all.isEmpty())
        assertNull(store().restore())
        RestTimer.start(60, "Squat", null)
        RestTimer.adjust(-60)
        assertTrue(prefs.all.isEmpty())
    }
}
