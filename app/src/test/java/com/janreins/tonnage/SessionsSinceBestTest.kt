package com.janreins.tonnage

import com.janreins.tonnage.domain.ExerciseSession
import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.domain.ProgressMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionsSinceBestTest {

    private val metric = ProgressMetric.ONE_REP_MAX

    private fun sessions(vararg values: Double) = values.mapIndexed { index, value ->
        ExerciseSession(workoutId = index.toLong(), startedAt = index * 1000L, sets = 3, values = mapOf(metric to value), bestSet = "")
    }

    @Test
    fun steadyIncreasesGiveZero() = assertEquals(0, Progress.sessionsSinceBest(sessions(80.0, 82.5, 85.0), metric))

    @Test
    fun aBestFollowedByThreeLowerOrEqualSessionsGivesThree() =
        assertEquals(3, Progress.sessionsSinceBest(sessions(80.0, 90.0, 85.0, 90.0, 70.0), metric))

    @Test
    fun anEqualValueIsNotANewBest() = assertEquals(2, Progress.sessionsSinceBest(sessions(90.0, 90.0, 90.0), metric))

    @Test
    fun oneSessionGivesZero() = assertEquals(0, Progress.sessionsSinceBest(sessions(80.0), metric))

    @Test
    fun noSessionsGiveZero() = assertEquals(0, Progress.sessionsSinceBest(emptyList(), metric))

    @Test
    fun newestTenSessionsAreListedNewestFirst() {
        val all = sessions(*DoubleArray(12) { it.toDouble() })
        assertEquals((11L downTo 2L).toList(), Progress.newestSessions(all).map { it.workoutId })
        assertEquals(listOf(1L, 0L), Progress.newestSessions(all.take(2)).map { it.workoutId })
    }
}
