package com.janreins.tonnage

import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.Progression
import com.janreins.tonnage.domain.ProgressionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {

    private fun set(weight: Double?, reps: Int?, type: SetType = SetType.NORMAL, position: Int = 0) =
        WorkoutSet(workoutExerciseId = 1, position = position, type = type, weightKg = weight, reps = reps, completed = true)

    private fun weighted(previous: List<WorkoutSet>, planned: List<WorkoutSet> = emptyList()) =
        Progression.hint(previous, TrackingType.WEIGHT_REPS, planned)

    @Test
    fun everySetHitTheTargetSoWeightGoesUp() {
        assertEquals(
            ProgressionHint("Last: 60 × 8, 8, 8 · try 62.5 × 8", 62.5, 8),
            weighted(listOf(set(60.0, 8), set(60.0, 8), set(60.0, 8))),
        )
    }

    @Test
    fun oneSetShortKeepsTheWeight() {
        assertEquals(
            ProgressionHint("Last: 60 × 8, 8, 7 · try 60 × 8 on all sets", 60.0, 8),
            weighted(listOf(set(60.0, 8), set(60.0, 8), set(60.0, 7))),
        )
    }

    @Test
    fun routineTargetsSetTheTargetReps() {
        val planned = listOf(set(60.0, 8, position = 0), set(60.0, 10, position = 1))
        assertEquals(
            ProgressionHint("Last: 60 × 8, 8 · try 60 × 10 on all sets", 60.0, 10),
            weighted(listOf(set(60.0, 8), set(60.0, 8)), planned),
        )
        // Reaching the highest target on every set moves the weight up.
        assertEquals(62.5, weighted(listOf(set(60.0, 10), set(60.0, 12)), planned)!!.weightKg!!, 0.0)
    }

    @Test
    fun withoutARoutineTheFirstSetsRepsAreTheTarget() {
        // First working set did 10, so the 8 at the top weight falls short.
        assertEquals(
            ProgressionHint("Last: 70 × 8 · try 70 × 10 on all sets", 70.0, 10),
            weighted(listOf(set(60.0, 10), set(70.0, 8))),
        )
    }

    @Test
    fun warmUpsAreIgnored() {
        val previous = listOf(set(40.0, 12, SetType.WARMUP), set(60.0, 8), set(60.0, 8))
        assertEquals(ProgressionHint("Last: 60 × 8, 8 · try 62.5 × 8", 62.5, 8), weighted(previous))
        // Warm-up targets don't raise the target reps either.
        assertEquals(62.5, weighted(previous, listOf(set(40.0, 12, SetType.WARMUP), set(60.0, 8)))!!.weightKg!!, 0.0)
        assertNull(weighted(listOf(set(40.0, 12, SetType.WARMUP))))
    }

    @Test
    fun bodyweightAddsOneRep() {
        assertEquals(
            ProgressionHint("Last: 12, 11, 10 · try 13", null, 13),
            Progression.hint(listOf(set(null, 12), set(null, 11), set(null, 10)), TrackingType.BODYWEIGHT_REPS, emptyList()),
        )
    }

    @Test
    fun durationAndDistanceGetNoHint() {
        val previous = listOf(WorkoutSet(workoutExerciseId = 1, position = 0, distanceKm = 5.0, durationSec = 1500, completed = true))
        assertNull(Progression.hint(previous, TrackingType.DURATION, emptyList()))
        assertNull(Progression.hint(previous, TrackingType.DISTANCE_DURATION, emptyList()))
    }

    @Test
    fun emptyOrZeroHistoryGetsNoHint() {
        assertNull(weighted(emptyList()))
        assertNull(weighted(listOf(set(0.0, 8), set(60.0, 0), set(null, 8))))
        assertNull(Progression.hint(listOf(set(null, 0)), TrackingType.BODYWEIGHT_REPS, emptyList()))
        assertNull(Progression.hint(emptyList(), TrackingType.BODYWEIGHT_REPS, emptyList()))
    }
}
