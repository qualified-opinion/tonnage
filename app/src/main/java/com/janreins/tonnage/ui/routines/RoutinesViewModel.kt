package com.janreins.tonnage.ui.routines

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.RoutineSummary
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.repository.RoutineRepository
import com.janreins.tonnage.data.settings.SettingsStore
import com.janreins.tonnage.domain.ExerciseBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The routine being edited, and how it looked when the editor opened. */
data class RoutineEditor(val original: RoutineDraft, val draft: RoutineDraft) {
    val hasChanges: Boolean get() = draft != original
}

class RoutinesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = RoutineRepository(db.routineDao(), db.exerciseDao())
    private val settings = SettingsStore.get(application)

    val routines: StateFlow<List<RoutineSummary>> = repository.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val library = db.exerciseDao().observeAll()
        .map { all -> all.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _editor = MutableStateFlow<RoutineEditor?>(null)
    val editor: StateFlow<RoutineEditor?> = _editor.asStateFlow()

    val editorBlocks: StateFlow<List<ExerciseBlock>> = combine(_editor, library) { editor, exercises ->
        editor?.draft?.toBlocks(exercises).orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var lastKey = 0L
    private fun nextKey(): Long = ++lastKey
    private var saving = false

    fun newRoutine() {
        val draft = RoutineDraft()
        _editor.value = RoutineEditor(draft, draft)
    }

    /** Opens the editor on a new routine copied from a workout; closing asks before discarding it. */
    fun newFromWorkout(name: String, blocks: List<ExerciseBlock>) {
        _editor.value = RoutineEditor(original = RoutineDraft(), draft = RoutineDraft.fromBlocks(name, blocks, ::nextKey))
    }

    fun edit(routineId: Long) {
        viewModelScope.launch {
            val contents = repository.load(routineId) ?: return@launch
            val draft = RoutineDraft.from(contents, ::nextKey)
            _editor.value = RoutineEditor(draft, draft)
        }
    }

    fun duplicate(routineId: Long) {
        viewModelScope.launch { repository.duplicate(routineId) }
    }

    fun delete(routineId: Long) {
        viewModelScope.launch { repository.delete(routineId) }
    }

    fun close() {
        _editor.value = null
    }

    fun save() {
        val draft = _editor.value?.draft ?: return
        if (!draft.canSave || saving) return
        saving = true
        viewModelScope.launch {
            try {
                repository.save(draft.id, draft.name, draft.toEntities())
                _editor.value = null
            } finally {
                saving = false
            }
        }
    }

    private fun update(transform: (RoutineDraft) -> RoutineDraft) {
        _editor.update { it?.copy(draft = transform(it.draft)) }
    }

    fun rename(name: String) = update { it.copy(name = name) }

    fun addExercises(exerciseIds: List<String>) =
        update { it.addExercises(exerciseIds, settings.defaultRestSeconds.value, ::nextKey) }

    fun removeExercise(block: ExerciseBlock) = update { it.removeExercise(block.workoutExercise.id) }
    fun moveExercise(block: ExerciseBlock, direction: Int) = update { it.moveExercise(block.workoutExercise.id, direction) }
    fun setRest(block: ExerciseBlock, seconds: Int) =
        update { it.updateExercise(block.workoutExercise.id) { exercise -> exercise.copy(restSeconds = seconds) } }

    fun addSet(block: ExerciseBlock) = update { it.addSet(block.workoutExercise.id, ::nextKey) }
    fun deleteSet(set: WorkoutSet) = update { it.deleteSet(set.id) }
    fun setType(set: WorkoutSet, type: SetType) = update { it.updateSet(set.id) { s -> s.copy(type = type) } }
    fun setWeight(set: WorkoutSet, kg: Double?) = update { it.updateSet(set.id) { s -> s.copy(weightKg = kg) } }
    fun setReps(set: WorkoutSet, reps: Int?) = update { it.updateSet(set.id) { s -> s.copy(reps = reps) } }
    fun setDistance(set: WorkoutSet, km: Double?) = update { it.updateSet(set.id) { s -> s.copy(distanceKm = km) } }
    fun setDuration(set: WorkoutSet, seconds: Int?) = update { it.updateSet(set.id) { s -> s.copy(durationSec = seconds) } }
}
