package com.janreins.tonnage.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.model.WorkoutSummary
import com.janreins.tonnage.data.repository.ActiveWorkout
import com.janreins.tonnage.data.repository.WorkoutRepository
import com.janreins.tonnage.data.settings.SettingsStore
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.domain.PersonalRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The history list, plus the finished workout that's open (to view or edit). */
class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    private val db: AppDatabase = AppDatabase.getInstance(application),
) : AndroidViewModel(application) {
    private val repository = WorkoutRepository(db.workoutDao(), db.exerciseDao())
    private val settings = SettingsStore.get(application)

    val workouts: StateFlow<List<WorkoutSummary>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    /** Records are paused during editing and stop immediately when neither history screen is visible. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<Map<Long, List<PersonalRecord>>> = _editing
        .flatMapLatest { editing -> if (editing) flowOf(emptyMap()) else repository.observeRecords() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyMap())

    private val _openId = MutableStateFlow<Long?>(null)
    val openId: StateFlow<Long?> = _openId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val opened: StateFlow<ActiveWorkout?> = _openId
        .flatMapLatest { id -> id?.let { repository.observeWorkout(it) } ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var openJob: Job? = null

    fun open(workoutId: Long) {
        openJob?.cancel()
        _editing.value = false
        _openId.value = null
        openJob = viewModelScope.launch {
            // Recover blank edit-mode rows left behind if the app was killed before Done.
            if (db.workoutDao().getWorkout(workoutId)?.endedAt != null) repository.tidyEdited(workoutId)
            _openId.value = workoutId
        }
    }

    fun close() {
        openJob?.cancel()
        if (_editing.value) finishEditing()
        _openId.value = null
    }

    fun startEditing() {
        _editing.value = true
    }

    fun finishEditing() {
        val id = _openId.value ?: return
        _editing.value = false
        viewModelScope.launch { repository.tidyEdited(id) }
    }

    fun delete() {
        openJob?.cancel()
        val id = _openId.value ?: return
        _editing.value = false
        _openId.value = null
        viewModelScope.launch { repository.discard(id) }
    }

    // Editing a finished workout writes straight to the database, as logging does.

    fun rename(title: String) {
        val id = _openId.value ?: return
        viewModelScope.launch { repository.rename(id, title) }
    }

    fun addExercises(exerciseIds: List<String>) {
        val id = _openId.value ?: return
        if (exerciseIds.isEmpty()) return
        viewModelScope.launch {
            repository.addExercises(id, exerciseIds, settings.defaultRestSeconds.value, completed = true)
        }
    }

    fun removeExercise(block: ExerciseBlock) {
        viewModelScope.launch { repository.removeExercise(block.workoutExercise.id) }
    }

    fun moveExercise(block: ExerciseBlock, direction: Int) {
        viewModelScope.launch {
            repository.moveExercise(block.workoutExercise.workoutId, block.workoutExercise.id, direction)
        }
    }

    fun setRest(block: ExerciseBlock, seconds: Int) {
        viewModelScope.launch { repository.setRest(block.workoutExercise.id, seconds) }
    }

    fun addSet(block: ExerciseBlock) {
        viewModelScope.launch { repository.addSet(block.workoutExercise.id, completed = true) }
    }

    fun deleteSet(set: WorkoutSet) {
        viewModelScope.launch { repository.deleteSet(set.id) }
    }

    fun setType(set: WorkoutSet, type: SetType) {
        viewModelScope.launch { repository.setType(set.id, type) }
    }

    fun setWeight(set: WorkoutSet, kg: Double?) {
        viewModelScope.launch { repository.setWeight(set.id, kg) }
    }

    fun setReps(set: WorkoutSet, reps: Int?) {
        viewModelScope.launch { repository.setReps(set.id, reps) }
    }

    fun setDistance(set: WorkoutSet, km: Double?) {
        viewModelScope.launch { repository.setDistance(set.id, km) }
    }

    fun setDuration(set: WorkoutSet, seconds: Int?) {
        viewModelScope.launch { repository.setDuration(set.id, seconds) }
    }
}
