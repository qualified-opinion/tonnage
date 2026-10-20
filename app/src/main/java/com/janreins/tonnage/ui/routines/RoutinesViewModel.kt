package com.janreins.tonnage.ui.routines

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.launch

/** The routine being edited, and how it looked when the editor opened. */
data class RoutineEditor(val original: RoutineDraft, val draft: RoutineDraft) {
    val hasChanges: Boolean get() = draft != original
}

class RoutinesViewModel @JvmOverloads constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val db: AppDatabase = AppDatabase.getInstance(application),
) : AndroidViewModel(application) {
    private val repository = RoutineRepository(db.routineDao(), db.exerciseDao())
    private val settings = SettingsStore.get(application)

    val routines: StateFlow<List<RoutineSummary>> = repository.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val library = db.exerciseDao().observeAll()
        .map { all -> all.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _editor = MutableStateFlow(RoutineDraftState.decode(savedState[RoutineDraftState.KEY]))
    val editor: StateFlow<RoutineEditor?> = _editor.asStateFlow()

    val editorBlocks: StateFlow<List<ExerciseBlock>> = combine(_editor, library) { editor, exercises ->
        editor?.draft?.toBlocks(exercises).orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _recoveryLimited = MutableStateFlow(false)
    val recoveryLimited: StateFlow<Boolean> = _recoveryLimited.asStateFlow()

    init {
        if (_editor.value == null) savedState.remove<String>(RoutineDraftState.KEY)
    }

    private var lastKey = _editor.value?.let { editor ->
        (editor.original.exercises + editor.draft.exercises)
            .flatMap { exercise -> listOf(exercise.key) + exercise.sets.map { it.key } }.maxOrNull()
    } ?: 0L
    private fun nextKey(): Long = ++lastKey
    private var saving = false

    fun newRoutine() {
        val draft = RoutineDraft()
        publish(RoutineEditor(draft, draft))
    }

    /** Opens the editor on a new routine copied from a workout; closing asks before discarding it. */
    fun newFromWorkout(name: String, blocks: List<ExerciseBlock>) {
        publish(RoutineEditor(original = RoutineDraft(), draft = RoutineDraft.fromBlocks(name, blocks, ::nextKey)))
    }

    fun edit(routineId: Long) {
        viewModelScope.launch {
            val contents = repository.load(routineId) ?: return@launch
            val draft = RoutineDraft.from(contents, ::nextKey)
            publish(RoutineEditor(draft, draft))
        }
    }

    fun duplicate(routineId: Long) {
        viewModelScope.launch { repository.duplicate(routineId) }
    }

    fun delete(routineId: Long) {
        viewModelScope.launch { repository.delete(routineId) }
    }

    fun close() {
        publish(null)
    }

    fun save() {
        val draft = _editor.value?.draft ?: return
        if (!draft.canSave || saving) return
        saving = true
        viewModelScope.launch {
            try {
                repository.save(draft.id, draft.name, draft.toEntities())
                publish(null)
            } finally {
                saving = false
            }
        }
    }

    private fun publish(editor: RoutineEditor?) {
        val encoded = editor?.let(RoutineDraftState::encode)
        if (encoded == null) savedState.remove<String>(RoutineDraftState.KEY)
        else savedState[RoutineDraftState.KEY] = encoded
        _recoveryLimited.value = editor != null && encoded == null
        _editor.value = editor
    }

    private fun update(transform: (RoutineDraft) -> RoutineDraft) {
        publish(_editor.value?.let { it.copy(draft = transform(it.draft)) })
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
