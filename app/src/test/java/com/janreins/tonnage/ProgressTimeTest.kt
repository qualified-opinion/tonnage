package com.janreins.tonnage

import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.domain.Streak
import com.janreins.tonnage.ui.progress.ProgressTime
import com.janreins.tonnage.ui.progress.progressTime
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressTimeTest {
    @Test fun nextMidnightSignalRespectsTheTwentyFiveHourDstDay() = runTest {
        val start = ZonedDateTime.parse("2026-10-25T00:00:00+02:00[Europe/Amsterdam]")
        val base = start.toInstant().toEpochMilli()
        val values = mutableListOf<ProgressTime>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            progressTime({ base + testScheduler.currentTime }, { start.zone }, MutableSharedFlow())
                .collect { values += it }
        }
        runCurrent()
        val untilMidnight = 25 * 60 * 60 * 1000L
        advanceTimeBy(untilMidnight - 1); runCurrent()
        assertEquals(1, values.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, values.size)
        assertEquals(ZonedDateTime.parse("2026-10-26T00:00:00+01:00[Europe/Amsterdam]").toInstant().toEpochMilli(), values.last().now)
        advanceTimeBy(24 * 60 * 60 * 1000L); runCurrent()
        assertEquals(3, values.size) // The following day is back to 24 hours.
    }

    @Test fun dstFallbackKeepsBothSundayOccurrencesInTheSameMondayToSundayWeek() {
        val sunday = ZonedDateTime.parse("2026-10-25T02:30:00+02:00[Europe/Amsterdam]")
        val laterSunday = sunday.withLaterOffsetAtOverlap()
        val monday = ZonedDateTime.parse("2026-10-26T00:00:00+01:00[Europe/Amsterdam]")
        val starts = listOf(sunday, laterSunday, monday).map { it.toInstant().toEpochMilli() }
        val weeks = Progress.weeks(starts, emptyList(), starts.last(), count = 2, zone = sunday.zone)
        assertEquals(listOf(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-26")), weeks.map { it.weekStart })
        assertEquals(listOf(2, 1), weeks.map { it.workouts })
        assertEquals(Streak(2, 2), Progress.streak(starts, starts.last(), sunday.zone))
    }

    @Test fun resumeReplacesTheOldMidnightWithTheNewZonesMidnight() = runTest {
        val base = java.time.Instant.parse("2026-10-25T15:59:00Z").toEpochMilli()
        var zone = java.time.ZoneId.of("UTC")
        val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val values = mutableListOf<ProgressTime>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            progressTime({ base + testScheduler.currentTime }, { zone }, resumes).collect { values += it }
        }
        runCurrent()
        zone = java.time.ZoneId.of("Asia/Manila")
        resumes.emit(Unit); runCurrent()
        assertEquals(zone, values.last().zone)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(3, values.size)
        assertEquals(LocalDate.parse("2026-10-26"), java.time.Instant.ofEpochMilli(values.last().now).atZone(zone).toLocalDate())
    }
}
