package com.janreins.tonnage

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.ui.theme.TonnageTheme
import com.janreins.tonnage.ui.workout.ExerciseCard
import com.janreins.tonnage.ui.workout.SetActions
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SetRowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val exercise = Exercise(id = "bench", name = "Bench", category = "strength", equipment = "barbell", primaryMuscles = "chest")

    @Before fun setUp() {
        // Create explicitly so both debug and release JVM tests need no test activity in the APK.
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    }

    @After fun tearDown() {
        activity.pause().stop().destroy()
    }

    private fun showSets(initial: List<WorkoutSet>, onToggle: (WorkoutSet) -> WorkoutSet = { it }) {
        val sets = mutableStateOf(initial)
        val actions = SetActions(
            onWeight = { _, _ -> }, onReps = { _, _ -> }, onDistance = { _, _ -> }, onDuration = { _, _ -> },
            onType = { _, _ -> },
            onDelete = { deleted -> sets.value = sets.value.filterNot { it.id == deleted.id } },
            onToggle = { toggled -> sets.value = sets.value.map { if (it.id == toggled.id) onToggle(it) else it } },
        )
        compose.runOnUiThread {
            activity.get().setContent {
                TonnageTheme(darkTheme = false) {
                    ExerciseCard(
                        block = ExerciseBlock(WorkoutExercise(id = 1, workoutId = 1, exerciseId = "bench", position = 0, restSeconds = 90), exercise, sets.value),
                        actions = actions, onAddSet = {}, onRestChange = {}, onRemove = {},
                    )
                }
            }
        }
    }

    @Test fun tickingFromAnEmptyFocusedCellImmediatelyShowsFilledValues() {
        showSets(listOf(WorkoutSet(id = 1, workoutExerciseId = 1, position = 0))) {
            // The same update Room emits after completing an empty set from last time's values.
            it.copy(weightKg = 60.0, reps = 8, completed = true)
        }
        val weight = compose.onAllNodes(hasSetTextAction())[0]
        weight.performClick().assertIsFocused().assertTextEquals("")
        compose.onNodeWithContentDescription("Mark set done").performClick()
        weight.assertTextEquals("60")
        compose.onAllNodes(hasSetTextAction())[1].assertTextEquals("8")
    }

    @Test fun deletingTheFocusedSetDoesNotMoveFocusIntoTheNextSet() {
        showSets((1L..3L).map { WorkoutSet(id = it, workoutExerciseId = 1, position = (it - 1).toInt(), weightKg = it * 10.0, reps = 8) })
        compose.onAllNodes(hasSetTextAction())[2].performClick().assertIsFocused()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText("Delete set").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(4)
        compose.onAllNodes(hasSetTextAction())[2].assertTextEquals("30").assertIsNotFocused()
    }
}
