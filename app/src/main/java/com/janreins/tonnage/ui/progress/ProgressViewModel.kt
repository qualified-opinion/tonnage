package com.janreins.tonnage.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.BodyWeight
import com.janreins.tonnage.domain.ExerciseProgress
import com.janreins.tonnage.domain.ExerciseSession
import com.janreins.tonnage.domain.MilestoneReport
import com.janreins.tonnage.domain.Milestones
import com.janreins.tonnage.domain.MonthStats
import com.janreins.tonnage.domain.MuscleSets
import com.janreins.tonnage.domain.Progress
import com.janreins.tonnage.domain.Streak
import com.janreins.tonnage.domain.WeekStats
import com.janreins.tonnage.domain.WorkoutStart
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
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
    /** The month shown in the month view, with its training days and muscles. */
    val month: MonthStats = MonthStats(YearMonth.now(), emptyMap(), 0, 0, 0.0, emptyList()),
    /** Today, to ring on the month calendar. */
    val today: LocalDate? = null,
    /** Whether an earlier month (back to the first workout) or a later one (up to now) can be shown. */
    val hasEarlierMonth: Boolean = false,
    val hasLaterMonth: Boolean = false,
    /** How many months before this one [month] is. */
    val monthsBack: Int = 0,
) {
    val week: WeekStats get() = weeks[selectedWeek]

    companion object {
        const val WEEKS = 12
    }
}

class ProgressViewModel @JvmOverloads constructor(
    application: Application,
    database: AppDatabase = AppDatabase.getInstance(application),
    clock: () -> Long = System::currentTimeMillis,
    zone: () -> ZoneId = ZoneId::systemDefault,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AndroidViewModel(application) {
    private val workoutDao = database.workoutDao()
    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val time = progressTime(clock, zone, resumes)
    private val sets = workoutDao.observeProgressSets()

    /** Which week is shown in detail: 0 is the current one, 1 the one before, and so on. */
    private val weeksBack = MutableStateFlow(0)

    /** Which month the month view shows: 0 is this month, 1 the one before, and so on. */
    private val monthsBack = MutableStateFlow(0)

    private val bodyWeights = database.bodyWeightDao().observeAll()

    private val history = combine(workoutDao.observeFinishedSummaries(), sets, bodyWeights) { workouts, allSets, weights ->
        Triple(workouts, allSets, weights)
    }

    val overview: StateFlow<ProgressOverview?> = combine(history, weeksBack, monthsBack, time) { (workouts, allSets, weights), back, monthBack, time ->
        val (now, zone) = time
        val starts = workouts.map { WorkoutStart(it.id, it.startedAt, it.endedAt) }
        val weeks = Progress.weeks(starts.map { it.startedAt }, allSets, now, ProgressOverview.WEEKS, zone)
        val selected = weeks.lastIndex - back.coerceIn(0, weeks.lastIndex)
        val thisMonth = Progress.monthOf(now, zone)
        val firstMonth = starts.minOfOrNull { it.startedAt }?.let { Progress.monthOf(it, zone) } ?: thisMonth
        val earliestBack = ChronoUnit.MONTHS.between(firstMonth, thisMonth).toInt().coerceAtLeast(0)
        val shownBack = monthBack.coerceIn(0, earliestBack)
        ProgressOverview(
            workoutCount = workouts.size,
            weeks = weeks,
            selectedWeek = selected,
            muscles = Progress.muscles(allSets, weeks[selected].weekStart, zone),
            streak = Progress.streak(starts.map { it.startedAt }, now, zone),
            milestones = Milestones.report(starts, allSets, now, zone, weights),
            exercises = Progress.exercises(allSets),
            month = Progress.month(starts.map { it.startedAt }, allSets, thisMonth.minusMonths(shownBack.toLong()), zone),
            today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            hasEarlierMonth = shownBack < earliestBack,
            hasLaterMonth = shownBack > 0,
            monthsBack = shownBack,
        )
    }.flowOn(dispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Body weight entries (oldest first) and today's date in the same zone as everything else here. */
    val bodyWeight: StateFlow<BodyWeightLog> = combine(bodyWeights, time) { entries, (now, zone) ->
        BodyWeightLog(entries, Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyWeightLog(emptyList(), LocalDate.now()))

    /** Re-read both wall time and the phone's zone, and reschedule midnight after a resume. */
    fun refresh() { resumes.tryEmit(Unit) }

    fun selectWeek(index: Int) {
        weeksBack.value = (ProgressOverview.WEEKS - 1 - index).coerceIn(0, ProgressOverview.WEEKS - 1)
    }

    fun previousWeek() {
        weeksBack.value = (weeksBack.value + 1).coerceAtMost(ProgressOverview.WEEKS - 1)
    }

    fun nextWeek() {
        weeksBack.value = (weeksBack.value - 1).coerceAtLeast(0)
    }

    fun previousMonth() {
        val shown = overview.value ?: return
        if (shown.hasEarlierMonth) monthsBack.value = shown.monthsBack + 1
    }

    fun nextMonth() {
        val shown = overview.value ?: return
        if (shown.hasLaterMonth) monthsBack.value = shown.monthsBack - 1
    }

    /** One exercise's history for its chart, oldest first. */
    fun sessions(exerciseId: String): Flow<List<ExerciseSession>> =
        sets.map { Progress.sessions(it, exerciseId) }.flowOn(Dispatchers.Default)
}

data class BodyWeightLog(val entries: List<BodyWeight>, val today: LocalDate)

internal data class ProgressTime(val now: Long, val zone: ZoneId)

/** Calendar midnights can be 23 or 25 hours apart; never assume a fixed 24-hour day. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun progressTime(clock: () -> Long, zone: () -> ZoneId, resumes: Flow<Unit>): Flow<ProgressTime> =
    resumes.onStart { emit(Unit) }.flatMapLatest {
        flow {
            while (true) {
                val time = ProgressTime(clock(), zone())
                emit(time)
                val midnight = Instant.ofEpochMilli(time.now).atZone(time.zone).toLocalDate()
                    .plusDays(1).atStartOfDay(time.zone).toInstant().toEpochMilli()
                delay((midnight - clock()).coerceAtLeast(1))
            }
        }
    }
