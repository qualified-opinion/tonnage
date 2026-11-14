package com.janreins.tonnage.ui.exercises

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.tonnage.data.backup.AutoBackup
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExercisesUiState(
    val isLoading: Boolean = true,
    val filter: ExerciseFilter = ExerciseFilter(),
    /** Everything else that matches (see [ExerciseFilter.sections]). */
    val exercises: List<Exercise> = emptyList(),
    val totalCount: Int = 0,
    /** Recently done exercises that match, with when they were last done. */
    val recent: List<Pair<Exercise, Long>> = emptyList(),
    /** Common gym lifts that match, shown after the recent ones. */
    val popular: List<Exercise> = emptyList(),
) {
    val isEmpty: Boolean get() = exercises.isEmpty() && recent.isEmpty() && popular.isEmpty()
}

/** How far back the recent list looks, so a search or muscle filter still finds a few. */
private const val RECENT_LOOKUP = 200

class ExercisesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExerciseRepository(AppDatabase.getInstance(application))
    private val filter = MutableStateFlow(ExerciseFilter())
    private val seeded = MutableStateFlow(false)

    val uiState: StateFlow<ExercisesUiState> =
        combine(
            repository.observeAll(),
            filter,
            seeded,
            AppDatabase.getInstance(application).workoutDao().observeRecentExercises(RECENT_LOOKUP),
        ) { all, currentFilter, isSeeded, recent ->
            val sections = currentFilter.sections(all, recent)
            ExercisesUiState(
                isLoading = !isSeeded && all.isEmpty(),
                filter = currentFilter,
                exercises = sections.rest,
                totalCount = all.size,
                recent = sections.recent,
                popular = sections.popular,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisesUiState())

    init {
        viewModelScope.launch {
            repository.seedLibrary(application)
            seeded.value = true
        }
    }

    fun observeExercise(id: String) = repository.observe(id)

    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setMuscle(muscle: String?) = filter.update { it.copy(muscle = muscle) }
    fun setEquipment(equipment: String?) = filter.update { it.copy(equipment = equipment) }
    fun setCustomOnly(customOnly: Boolean) = filter.update { it.copy(customOnly = customOnly) }
    fun clearFilters() = filter.update { ExerciseFilter(query = it.query) }

    fun addCustom(name: String, primaryMuscle: String, equipment: String, trackingType: TrackingType) {
        viewModelScope.launch {
            repository.addCustom(name, primaryMuscle, equipment, trackingType)
            AutoBackup.afterChange(getApplication())
        }
    }

    fun deleteCustom(id: String, onResult: (deleted: Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = repository.deleteCustom(id)
            if (deleted) AutoBackup.afterChange(getApplication())
            onResult(deleted)
        }
    }
}
