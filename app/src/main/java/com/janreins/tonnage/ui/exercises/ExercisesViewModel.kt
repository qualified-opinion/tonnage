package com.janreins.tonnage.ui.exercises

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val exercises: List<Exercise> = emptyList(),
    val totalCount: Int = 0,
)

class ExercisesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExerciseRepository(AppDatabase.getInstance(application).exerciseDao())
    private val filter = MutableStateFlow(ExerciseFilter())
    private val seeded = MutableStateFlow(false)

    val uiState: StateFlow<ExercisesUiState> =
        combine(repository.observeAll(), filter, seeded) { all, currentFilter, isSeeded ->
            ExercisesUiState(
                isLoading = !isSeeded && all.isEmpty(),
                filter = currentFilter,
                exercises = currentFilter.apply(all),
                totalCount = all.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisesUiState())

    init {
        viewModelScope.launch {
            repository.seedIfEmpty(application)
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
        viewModelScope.launch { repository.addCustom(name, primaryMuscle, equipment, trackingType) }
    }

    fun deleteCustom(id: String, onResult: (deleted: Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.deleteCustom(id)) }
    }
}
