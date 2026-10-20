package com.janreins.tonnage

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.ui.routines.RoutineDraftState
import com.janreins.tonnage.ui.routines.RoutinesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private val store = ViewModelStore()
    private var modelId = 0

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        db.close()
        Dispatchers.resetMain()
    }

    private fun model(handle: SavedStateHandle) = RoutinesViewModel(app, handle, db)
        .also { store.put("routine-${modelId++}", it) }

    private fun recovered(handle: SavedStateHandle) = model(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))

    @Test fun recoveryKeepsTheWholeDraftAndGeneratesFreshKeys() {
        val handle = SavedStateHandle()
        val first = model(handle)
        first.newRoutine(); first.rename("Push A"); first.addExercises(listOf("bench", "squat"))
        val blocks = first.editor.value!!.draft.toBlocks(emptyMap())
        first.setRest(blocks[0], 150)
        val set = blocks[0].sets.single()
        first.setWeight(set, 62.5); first.setReps(set, 8); first.setType(set, SetType.DROP)
        first.setDistance(set, 1.5); first.setDuration(set, 90)
        first.addSet(blocks[0]); first.moveExercise(blocks[1], -1)
        val before = first.editor.value!!
        val next = recovered(handle)
        assertEquals(before, next.editor.value)
        assertTrue(next.editor.value!!.hasChanges)
        next.addExercises(listOf("ohp"))
        val keys = next.editor.value!!.draft.exercises.flatMap { listOf(it.key) + it.sets.map { s -> s.key } }
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.last() > before.draft.exercises.flatMap { listOf(it.key) + it.sets.map { s -> s.key } }.max())
    }

    @Test fun saveAndCancelClearTheCheckpointAndExistingRoutineIdentitySurvives() = runTest {
        db.exerciseDao().insertAll(listOf(Exercise(id = "bench", name = "Bench", category = "strength", equipment = "barbell", primaryMuscles = "chest")))
        val handle = SavedStateHandle()
        val first = model(handle)
        first.newRoutine(); first.rename("Push"); first.addExercises(listOf("bench"))
        first.save(); dispatcher.scheduler.runCurrent()
        assertNull(first.editor.value)
        assertNull(recovered(handle).editor.value)
        assertFalse(handle.contains(RoutineDraftState.KEY))
        val id = db.routineDao().observeRoutines().first().single().id
        first.edit(id); dispatcher.scheduler.runCurrent()
        first.rename("Push edited")
        val edited = recovered(handle)
        assertEquals(first.editor.value, edited.editor.value)
        assertEquals(id, edited.editor.value!!.draft.id)
        assertEquals("Push", edited.editor.value!!.original.name)
        assertTrue(edited.editor.value!!.hasChanges)
        first.close()
        assertFalse(handle.contains(RoutineDraftState.KEY))
        assertNull(recovered(handle).editor.value)
    }

    @Test fun oversizedDraftStaysEditableWithoutSavingAnOversizedOrStaleCheckpoint() {
        val handle = SavedStateHandle()
        val model = model(handle)
        model.newRoutine()
        assertTrue(handle.contains(RoutineDraftState.KEY))
        val large = "a".repeat(RoutineDraftState.MAX_CHARS + 1)
        model.rename(large)
        assertEquals(large, model.editor.value!!.draft.name)
        assertTrue(model.recoveryLimited.value)
        assertFalse(handle.contains(RoutineDraftState.KEY))
        model.rename("Small again")
        assertFalse(model.recoveryLimited.value)
        assertEquals(model.editor.value, recovered(handle).editor.value)
        model.close()
        assertFalse(model.recoveryLimited.value)
    }

    @Test fun malformedSavedDraftIsDroppedWithoutCrashing() {
        val handle = SavedStateHandle(mapOf(RoutineDraftState.KEY to "not-json"))
        assertNull(model(handle).editor.value)
        assertFalse(handle.contains(RoutineDraftState.KEY))
    }
}
