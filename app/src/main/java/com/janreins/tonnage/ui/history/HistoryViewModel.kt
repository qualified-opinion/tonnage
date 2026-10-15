package com.janreins.tonnage.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.WorkoutSummary
import com.janreins.tonnage.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repository = WorkoutRepository(db.workoutDao(), db.exerciseDao())

    val workouts: StateFlow<List<WorkoutSummary>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
