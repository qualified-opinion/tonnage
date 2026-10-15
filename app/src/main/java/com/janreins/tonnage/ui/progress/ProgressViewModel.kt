package com.janreins.tonnage.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.domain.ExerciseProgress
import com.janreins.tonnage.domain.ExerciseSession
import com.janreins.tonnage.domain.MilestoneReport
import com.janreins.tonnage.domain.Milestones
import com.janreins.tonnage.domain.MuscleSets
import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.domain.Streak
import com.janreins.tonnage.domain.WeekStats
import com.janreins.tonnage.domain.WorkoutStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything on the Progress tab, worked out from finished workouts. */
data class ProgressOverview(
    val workoutCount: Int,
    /** The last [WEEKS] weeks, oldest first. */
    val weeks: List<WeekStats>,
    /** Index into [weeks] of the week shown in detail. */
    val selectedWeek: Int,
    val muscles: List<MuscleSets>,
    val streak: Streak,
    val milestones: MilestoneReport,
    val exercises: List<ExerciseProgress>,
) {
    val week: WeekStats get() = weeks[selectedWeek]

    companion object {
        const val WEEKS = 12
    }
}

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val workoutDao = AppDatabase.getInstance(application).workoutDao()
    private val sets = workoutDao.observeProgressSets()

    /** Which week is shown in detail: 0 is the current one, 1 the one before, and so on. */
    private val weeksBack = MutableStateFlow(0)

    val overview: StateFlow<ProgressOverview?> = combine(workoutDao.observeFinishedSummaries(), sets, weeksBack) { workouts, allSets, back ->
        val now = System.currentTimeMillis()
        val starts = workouts.map { WorkoutStart(it.id, it.startedAt) }
        val weeks = Progress.weeks(starts.map { it.startedAt }, allSets, now, ProgressOverview.WEEKS)
        val selected = weeks.lastIndex - back.coerceIn(0, weeks.lastIndex)
        ProgressOverview(
            workoutCount = workouts.size,
            weeks = weeks,
            selectedWeek = selected,
            muscles = Progress.muscles(allSets, weeks[selected].weekStart),
            streak = Progress.streak(starts.map { it.startedAt }, now),
            milestones = Milestones.report(starts, allSets, now),
            exercises = Progress.exercises(allSets),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectWeek(index: Int) {
        weeksBack.value = (ProgressOverview.WEEKS - 1 - index).coerceIn(0, ProgressOverview.WEEKS - 1)
    }

    fun previousWeek() {
        weeksBack.value = (weeksBack.value + 1).coerceAtMost(ProgressOverview.WEEKS - 1)
    }

    fun nextWeek() {
        weeksBack.value = (weeksBack.value - 1).coerceAtLeast(0)
    }

    /** One exercise's history for its chart, oldest first. */
    fun sessions(exerciseId: String): Flow<List<ExerciseSession>> =
        sets.map { Progress.sessions(it, exerciseId) }.flowOn(Dispatchers.Default)
}
