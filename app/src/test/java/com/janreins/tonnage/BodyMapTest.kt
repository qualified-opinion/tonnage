package com.janreins.tonnage

import com.janreins.tonnage.data.model.ExerciseOptions
import com.janreins.tonnage.ui.components.BodyMapGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMapTest {

    @Test
    fun everyLibraryMuscleIsOnTheMap() {
        assertEquals(ExerciseOptions.muscles.toSet(), BodyMapGeometry.muscles)
    }

    @Test
    fun tapsFindTheMuscleUnderThem() {
        val front = BodyMapGeometry.front
        val back = BodyMapGeometry.back
        assertEquals("chest", BodyMapGeometry.muscleAt(front, 42f, 47f))
        assertEquals("chest", BodyMapGeometry.muscleAt(front, 58f, 47f))
        assertEquals("quadriceps", BodyMapGeometry.muscleAt(front, 40f, 130f))
        assertEquals("abdominals", BodyMapGeometry.muscleAt(front, 46f, 80f))
        assertEquals("lats", BodyMapGeometry.muscleAt(back, 37f, 65f))
        assertEquals("glutes", BodyMapGeometry.muscleAt(back, 58f, 112f))
        assertEquals("hamstrings", BodyMapGeometry.muscleAt(back, 41f, 138f))
        assertNull(BodyMapGeometry.muscleAt(front, 5f, 5f))
        assertNull(BodyMapGeometry.muscleAt(front, 50f, 13f))
    }

    @Test
    fun regionsStayInsideTheFigure() {
        (BodyMapGeometry.silhouette + BodyMapGeometry.front + BodyMapGeometry.back).flatMap { it.points }.forEach {
            assertTrue(it.x in 0f..BodyMapGeometry.WIDTH && it.y in 0f..BodyMapGeometry.HEIGHT)
        }
    }

    @Test
    fun shadeGrowsWithSets() {
        assertEquals(0f, BodyMapGeometry.shade(0, 10), 0f)
        assertEquals(1f, BodyMapGeometry.shade(10, 10), 0.0001f)
        assertTrue(BodyMapGeometry.shade(3, 10) in 0.35f..BodyMapGeometry.shade(6, 10))
    }
}
