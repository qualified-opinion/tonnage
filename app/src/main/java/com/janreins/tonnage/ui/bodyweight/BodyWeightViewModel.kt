package com.janreins.tonnage.ui.bodyweight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.tonnage.data.backup.AutoBackup
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.BodyWeight
import com.janreins.tonnage.domain.BodyWeights
import com.janreins.tonnage.ui.progress.progressTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The body weight log: entries newest first, saving one per day and deleting. */
class BodyWeightViewModel @JvmOverloads constructor(
    application: Application,
    database: AppDatabase = AppDatabase.getInstance(application),
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : AndroidViewModel(application) {
    private val dao = database.bodyWeightDao()

    val entries: StateFlow<List<BodyWeight>> = dao.observeAll()
        .map { it.reversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()

    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Today's date, moving on at midnight and re-read on [refresh], so the screen can pick the new day. */
    val currentDay: StateFlow<LocalDate> = progressTime(clock, zone, resumes)
        .map { Instant.ofEpochMilli(it.now).atZone(it.zone).toLocalDate() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), today())

    /** Re-read the wall clock and the phone's zone, for example after the app comes back. */
    fun refresh() { resumes.tryEmit(Unit) }

    /** Saves [weightKg] for [date], replacing that day's entry. Returns false for a future date or an impossible weight. */
    fun save(date: LocalDate, weightKg: Double?): Boolean {
        if (!BodyWeights.canSave(date, weightKg, today())) return false
        viewModelScope.launch {
            dao.upsert(date.toEpochDay(), weightKg!!, clock())
            AutoBackup.afterChange(getApplication())
        }
        return true
    }

    fun delete(entry: BodyWeight) {
        viewModelScope.launch {
            dao.delete(entry.id)
            AutoBackup.afterChange(getApplication())
        }
    }
}
