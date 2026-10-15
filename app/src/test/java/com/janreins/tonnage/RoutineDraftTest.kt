package com.janreins.tonnage

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.ui.routines.RoutineDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDraftTest {

    private var key = 0L
    private val nextKey = { ++key }

    @Test
    fun needsANameAndAnExerciseToSave() {
        val empty = RoutineDraft(name = "Legs")
        assertFalse(empty.canSave)
        val withExercise = empty.addExercises(listOf("squat"), restSeconds = 90, nextKey)
        assertTrue(withExercise.canSave)
        assertFalse(withExercise.copy(name = "  ").canSave)
    }

    @Test
    fun addedSetsCopyTheLastOneAsANormalSet() {
        var draft = RoutineDraft(name = "Push").addExercises(listOf("bench"), restSeconds = 90, nextKey)
        val exercise = draft.exercises.single()
        draft = draft.updateSet(exercise.sets.single().key) { it.copy(type = SetType.WARMUP, weightKg = 40.0, reps = 10) }
            .addSet(exercise.key, nextKey)

        val sets = draft.exercises.single().sets
        assertEquals(2, sets.size)
        assertEquals(SetType.NORMAL, sets[1].type)
        assertEquals(40.0 to 10, sets[1].weightKg to sets[1].reps)
        assertTrue(sets[0].key != sets[1].key)

        draft = draft.deleteSet(sets[0].key)
        assertEquals(listOf(sets[1].key), draft.exercises.single().sets.map { it.key })
    }

    @Test
    fun movingStaysInsideTheList() {
        val draft = RoutineDraft(name = "Push").addExercises(listOf("bench", "ohp", "dips"), restSeconds = 90, nextKey)
        val dips = draft.exercises[2].key

        assertEquals(listOf("bench", "dips", "ohp"), draft.moveExercise(dips, -1).exercises.map { it.exerciseId })
        assertEquals(draft, draft.moveExercise(dips, 1))
        assertEquals(listOf("ohp", "dips"), draft.removeExercise(draft.exercises[0].key).exercises.map { it.exerciseId })
    }

    @Test
    fun blocksAndEntitiesFollowTheDraftOrder() {
        val draft = RoutineDraft(id = 5, name = "Push").addExercises(listOf("bench", "missing"), restSeconds = 60, nextKey)
        val bench = Exercise(id = "bench", name = "Bench Press", category = "strength", equipment = "barbell", primaryMuscles = "chest")

        val blocks = draft.toBlocks(mapOf("bench" to bench))
        assertEquals(listOf("Bench Press", "Unknown exercise"), blocks.map { it.name })
        assertEquals(draft.exercises.map { it.key }, blocks.map { it.workoutExercise.id })
        assertEquals(draft.exercises[0].sets.single().key, blocks[0].sets.single().id)

        val entities = draft.toEntities()
        assertEquals(listOf(0, 1), entities.map { it.first.position })
        assertEquals(listOf(60, 60), entities.map { it.first.restSeconds })
        assertEquals(listOf(1, 1), entities.map { it.second.size })
    }
}
