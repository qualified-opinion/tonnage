package com.janreins.tonnage

import com.janreins.tonnage.data.model.ExerciseOptions
import com.janreins.tonnage.ui.components.BodyMapArt
import com.janreins.tonnage.ui.components.PathStep
import com.janreins.tonnage.ui.components.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BodyMapTest {

    private val art by lazy {
        val file = listOf("src/main/assets", "app/src/main/assets").map { File(it, BodyMapArt.ASSET_NAME) }.first { it.exists() }
        BodyMapArt.parse(file.readText())
    }

    @Test
    fun everyLibraryMuscleIsOnTheMap() {
        assertEquals(ExerciseOptions.muscles.toSet(), art.muscles)
    }

    @Test
    fun tapsFindTheMuscleUnderneath() {
        assertEquals("chest", art.front.muscleAt(357f, 256f))
        assertEquals("abdominals", art.front.muscleAt(293f, 502f))
        assertEquals("quadriceps", art.front.muscleAt(386f, 665f))
        assertEquals("biceps", art.front.muscleAt(446f, 317f))
        assertEquals("forearms", art.front.muscleAt(498f, 408f))
        assertEquals("adductors", art.front.muscleAt(305f, 611f))
        assertEquals("calves", art.front.muscleAt(366f, 929f))
        assertEquals("lower back", art.back.muscleAt(269f, 442f))
        assertEquals("lats", art.back.muscleAt(356f, 365f))
        assertEquals("middle back", art.back.muscleAt(393f, 251f))
        assertEquals("traps", art.back.muscleAt(355f, 211f))
        assertEquals("triceps", art.back.muscleAt(436f, 342f))
        assertEquals("glutes", art.back.muscleAt(349f, 540f))
        assertEquals("abductors", art.back.muscleAt(389f, 508f))
        assertEquals("hamstrings", art.back.muscleAt(216f, 711f))
        assertEquals("calves", art.back.muscleAt(366f, 873f))
        assertEquals("neck", art.back.muscleAt(329f, 123f))
    }

    @Test
    fun skinHandsFaceAndEmptySpaceAreNotMuscles() {
        assertNull(art.front.muscleAt(5f, 5f)) // empty space
        assertNull(art.front.muscleAt(293f, 60f)) // forehead
        assertNull(art.front.muscleAt(60f, 610f)) // hand
        assertNull(art.back.muscleAt(298f, 60f)) // back of the head
    }

    @Test
    fun tapsNameTheExactMuscle() {
        assertEquals("Front delts", art.front.regionAt(176f, 236f)?.part)
        assertEquals("Side delts", art.front.regionAt(430f, 214f)?.part)
        assertEquals("Rear delts", art.back.regionAt(176f, 236f)?.part)
        assertEquals("Inner quad (teardrop)", art.front.regionAt(329f, 706f)?.part)
        assertEquals("Triceps long head", art.back.regionAt(436f, 342f)?.part)
        assertEquals("shoulders", art.back.regionAt(176f, 236f)?.muscle)
    }

    @Test
    fun everyMuscleShapeHasAGroupAndAName() {
        (art.front.shapes + art.back.shapes).filter { it.kind == ShapeKind.MUSCLE }.forEach {
            assertTrue(it.muscle in ExerciseOptions.muscles && !it.part.isNullOrBlank())
        }
    }

    @Test
    fun shapesStayInsideTheFigure() {
        listOf(art.front, art.back).forEach { view ->
            assertTrue(view.shapes.size > 50)
            view.shapes.forEach { shape ->
                assertTrue(shape.left >= 0f && shape.right <= view.width && shape.top >= 0f && shape.bottom <= view.height)
            }
        }
    }

    @Test
    fun readsEveryPathCommandTheArtUses() {
        val steps = BodyMapArt.parsePath("M1 2H5V6L7 8C1 2 3 4 5.5-6Z")
        assertEquals(
            listOf(
                PathStep.MoveTo(1f, 2f),
                PathStep.LineTo(5f, 2f),
                PathStep.LineTo(5f, 6f),
                PathStep.LineTo(7f, 8f),
                PathStep.CubicTo(1f, 2f, 3f, 4f, 5.5f, -6f),
                PathStep.Close,
            ),
            steps,
        )
    }

    @Test
    fun shadeGrowsWithSets() {
        assertEquals(0f, BodyMapArt.shade(0, 10), 0f)
        assertEquals(1f, BodyMapArt.shade(10, 10), 0.0001f)
        assertEquals(1f, BodyMapArt.shade(14, 10), 0.0001f)
        assertTrue(BodyMapArt.shade(3, 10) in 0.01f..BodyMapArt.shade(6, 10))
    }
}
