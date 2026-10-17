package com.janreins.tonnage.ui.workout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.repository.ActiveWorkout
import com.janreins.tonnage.data.repository.RoutineRepository
import com.janreins.tonnage.data.repository.WorkoutRepository
import com.janreins.tonnage.data.settings.SettingsStore
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.domain.PersonalRecord
import com.janreins.tonnage.domain.SessionSummary
import com.janreins.tonnage.domain.WorkoutStats
import com.janreins.tonnage.service.WorkoutService
import com.janreins.tonnage.timer.RestTimer
import com.janreins.tonnage.timer.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shown before saving: totals for the session and any records it set. */
data class FinishPreview(val summary: SessionSummary, val records: List<PersonalRecord>)

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = WorkoutRepository(db.workoutDao(), db.exerciseDao())
    private val routines = RoutineRepository(db.routineDao(), db.exerciseDao())
    private val settings = SettingsStore.get(application)

    /** False until the database has answered whether a workout is active. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val active: StateFlow<ActiveWorkout?> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Last time's completed sets per exercise id, for the "previous" column. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val previous: StateFlow<Map<String, List<WorkoutSet>>> = active
        .map { workout -> workout?.let { it.workout.routineId to it.blocks.map { b -> b.workoutExercise.exerciseId }.toSet() } }
        .distinctUntilChanged()
        .mapLatest { key -> key?.let { (routineId, ids) -> repository.previousSets(ids, routineId) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** An unfinished workout found after the app was killed, waiting for resume or discard. */
    private val _pendingResume = MutableStateFlow<Workout?>(null)
    val pendingResume: StateFlow<Workout?> = _pendingResume.asStateFlow()

    private val _finishPreview = MutableStateFlow<FinishPreview?>(null)
    val finishPreview: StateFlow<FinishPreview?> = _finishPreview.asStateFlow()

    val restTimer: StateFlow<RestTimer.State?> = RestTimer.state

    init {
        viewModelScope.launch {
            val existing = repository.getActive()
            if (existing != null && !WorkoutSession.confirmedInThisProcess) {
                _pendingResume.value = existing
            } else if (existing != null) {
                startService()
            }
            _loaded.value = true
        }
        // Keep the ongoing notification in step with the workout.
        viewModelScope.launch {
            combine(active, _pendingResume) { workout, pending -> workout to pending }.collect { (workout, pending) ->
                WorkoutSession.info.value = if (workout != null && pending == null) {
                    WorkoutSession.Info(
                        title = workout.workout.title,
                        startedAt = workout.workout.startedAt,
                        completedSets = workout.blocks.sumOf { block -> block.sets.count { it.completed } },
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun startService() {
        WorkoutSession.confirmedInThisProcess = true
        WorkoutService.start(getApplication())
    }

    fun startEmptyWorkout() {
        viewModelScope.launch {
            repository.startEmpty()
            _pendingResume.value = null
            startService()
        }
    }

    fun startRoutine(routineId: Long) {
        viewModelScope.launch {
            val contents = routines.load(routineId) ?: return@launch
            repository.startFromRoutine(contents)
            _pendingResume.value = null
            startService()
        }
    }

    fun resumePending() {
        _pendingResume.value = null
        startService()
    }

    fun discardPending() {
        val workout = _pendingResume.value ?: return
        viewModelScope.launch {
            repository.discard(workout.id)
            _pendingResume.value = null
        }
    }

    fun rename(title: String) {
        val workout = active.value?.workout ?: return
        viewModelScope.launch { repository.rename(workout.id, title) }
    }

    fun addExercises(exerciseIds: List<String>) {
        val workout = active.value?.workout ?: return
        if (exerciseIds.isEmpty()) return
        viewModelScope.launch {
            repository.addExercises(workout.id, exerciseIds, settings.defaultRestSeconds.value)
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

    fun usePrevious(set: WorkoutSet, previous: WorkoutSet) {
        viewModelScope.launch { repository.copyValues(set.id, previous) }
    }

    fun setRest(block: ExerciseBlock, seconds: Int) {
        viewModelScope.launch { repository.setRest(block.workoutExercise.id, seconds) }
    }

    fun addSet(block: ExerciseBlock) {
        viewModelScope.launch { repository.addSet(block.workoutExercise.id) }
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

    /**
     * Ticks a set off (starting the rest timer) or un-ticks it. A blank set is filled from last
     * time's set at the same position; with nothing to fill it from, the tick is ignored.
     */
    fun toggleCompleted(block: ExerciseBlock, set: WorkoutSet) {
        if (set.completed) {
            viewModelScope.launch { repository.setCompleted(set.id, false) }
            return
        }
        val index = block.sets.indexOfFirst { it.id == set.id }
        val last = previous.value[block.workoutExercise.exerciseId]?.getOrNull(index)
        viewModelScope.launch {
            if (!repository.complete(set.id, block.trackingType, last)) return@launch
            val blocks = active.value?.blocks.orEmpty().map { b ->
                if (b.workoutExercise.id == block.workoutExercise.id) {
                    b.copy(sets = b.sets.map { if (it.id == set.id) it.copy(completed = true) else it })
                } else {
                    b
                }
            }
            RestTimer.start(block.workoutExercise.restSeconds, block.name, WorkoutStats.nextSetLabel(blocks, set.id))
        }
    }

    fun adjustRest(seconds: Int) = RestTimer.adjust(seconds)
    fun skipRest() = RestTimer.skip()

    fun prepareFinish() {
        val current = active.value ?: return
        viewModelScope.launch {
            _finishPreview.value = FinishPreview(
                summary = WorkoutStats.summarize(current.blocks, current.workout.startedAt, System.currentTimeMillis()),
                records = repository.recordsFor(current),
            )
        }
    }

    fun cancelFinish() {
        _finishPreview.value = null
    }

    fun finish(onSaved: () -> Unit) {
        val workout = active.value?.workout ?: return
        viewModelScope.launch {
            repository.finish(workout.id)
            endSession()
            onSaved()
        }
    }

    /** Ticks every filled set that wasn't ticked, then saves the workout. */
    fun completeFilledAndFinish(onSaved: () -> Unit) {
        val current = active.value ?: return
        viewModelScope.launch {
            repository.completeFilled(current.blocks)
            repository.finish(current.workout.id)
            endSession()
            onSaved()
        }
    }

    fun discard() {
        val workout = active.value?.workout ?: return
        viewModelScope.launch {
            repository.discard(workout.id)
            endSession()
        }
    }

    private fun endSession() {
        _finishPreview.value = null
        RestTimer.skip()
        WorkoutSession.info.value = null
        WorkoutSession.confirmedInThisProcess = false
        WorkoutService.stop(getApplication())
    }
}
