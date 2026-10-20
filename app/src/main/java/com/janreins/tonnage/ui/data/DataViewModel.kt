package com.janreins.tonnage.ui.data

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.tonnage.data.backup.BackupContents
import com.janreins.tonnage.data.backup.BackupFormatException
import com.janreins.tonnage.data.backup.BackupJson
import com.janreins.tonnage.data.backup.BackupRepository
import com.janreins.tonnage.data.backup.BackupTooLargeException
import com.janreins.tonnage.data.backup.WorkoutInProgressException
import com.janreins.tonnage.data.hevy.FileTooLargeException
import com.janreins.tonnage.data.hevy.HevyFormatException
import com.janreins.tonnage.data.hevy.BACKUP_BYTE_LIMIT
import com.janreins.tonnage.data.hevy.BACKUP_TOO_LARGE
import com.janreins.tonnage.data.hevy.HEVY_BYTE_LIMIT
import com.janreins.tonnage.data.hevy.HEVY_TOO_LARGE
import com.janreins.tonnage.data.hevy.readBoundedText
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.repository.ExerciseChoice
import com.janreins.tonnage.data.repository.ImportPlan
import com.janreins.tonnage.data.repository.ImportRepository
import com.janreins.tonnage.data.repository.ImportResult
import com.janreins.tonnage.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Hevy import, step by step. */
sealed interface ImportState {
    data object Reading : ImportState
    data class Failed(val message: String) : ImportState
    /** Parsed and matched; [reviewing] shows the exercise list instead of the summary. */
    data class Ready(val plan: ImportPlan, val choices: Map<String, ExerciseChoice>, val reviewing: Boolean) : ImportState
    data object Importing : ImportState
    data class Done(val result: ImportResult) : ImportState
}

/** Backup and restore, as shown on the More tab. */
sealed interface BackupState {
    data object Working : BackupState
    data class Saved(val workouts: Int) : BackupState
    /** A backup was read and is waiting for the user to confirm replacing everything. */
    data class ConfirmRestore(val contents: BackupContents) : BackupState
    data class Restored(val workouts: Int) : BackupState
    data class Failed(val message: String) : BackupState
}

/** Hevy import plus JSON backup and restore: the More tab's data tools. */
class DataViewModel @JvmOverloads constructor(
    application: Application,
    private val encodeBackup: (BackupContents) -> ByteArray = { BackupJson.encode(it) },
) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val settings = SettingsStore.get(application)
    private val importer = ImportRepository(db, settings)
    private val backups = BackupRepository(db)

    val library: StateFlow<Map<String, Exercise>> = db.exerciseDao().observeAll()
        .map { all -> all.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _import = MutableStateFlow<ImportState?>(null)
    val importState: StateFlow<ImportState?> = _import.asStateFlow()

    /** The Hevy name the exercise picker is choosing for, while it's open. */
    private val _picking = MutableStateFlow<String?>(null)
    val picking: StateFlow<String?> = _picking.asStateFlow()

    private val _backup = MutableStateFlow<BackupState?>(null)
    val backupState: StateFlow<BackupState?> = _backup.asStateFlow()

    private suspend fun readText(uri: Uri, limit: Int, tooLargeMessage: String): String {
        val context = currentCoroutineContext()
        return getApplication<Application>().contentResolver.openInputStream(uri)?.use {
            readBoundedText(it, limit, tooLargeMessage) { context.ensureActive() }
        } ?: throw IllegalStateException("Couldn't open the file.")
    }

    // Hevy import

    private var importReadJob: Job? = null
    private var importOperationId = 0L

    fun startImport(uri: Uri) {
        if (_import.value is ImportState.Importing) return
        val operationId = ++importOperationId
        importReadJob?.cancel()
        _picking.value = null
        _import.value = ImportState.Reading
        importReadJob = viewModelScope.launch {
            val state = try {
                val plan = withContext(Dispatchers.IO) {
                    importer.prepare(readText(uri, HEVY_BYTE_LIMIT, HEVY_TOO_LARGE))
                }
                ImportState.Ready(plan, plan.defaultChoices(), reviewing = plan.matches.any { it.confidence.needsReview })
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileTooLargeException) {
                ImportState.Failed(e.message!!)
            } catch (e: HevyFormatException) {
                ImportState.Failed(e.message ?: "This file couldn't be read.")
            } catch (e: Exception) {
                ImportState.Failed("This file couldn't be read: ${e.message ?: e.javaClass.simpleName}")
            }
            if (operationId == importOperationId) _import.value = state
        }
    }

    private fun updateReady(transform: (ImportState.Ready) -> ImportState.Ready) {
        _import.update { state -> if (state is ImportState.Ready) transform(state) else state }
    }

    fun choose(hevyName: String, choice: ExerciseChoice) = updateReady { it.copy(choices = it.choices + (hevyName to choice)) }
    fun showReview(reviewing: Boolean) = updateReady { it.copy(reviewing = reviewing) }

    fun pickFor(hevyName: String?) {
        _picking.value = hevyName
    }

    fun picked(exercise: Exercise) {
        val name = _picking.value ?: return
        choose(name, ExerciseChoice.Existing(exercise.id))
        _picking.value = null
    }

    fun runImport() {
        val ready = _import.value as? ImportState.Ready ?: return
        _import.value = ImportState.Importing
        viewModelScope.launch {
            _import.value = try {
                ImportState.Done(importer.import(ready.plan, ready.choices, settings.defaultRestSeconds.value))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportState.Failed("The import failed and nothing was saved: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun closeImport() {
        if (_import.value is ImportState.Importing) return
        ++importOperationId
        importReadJob?.cancel()
        importReadJob = null
        _import.value = null
        _picking.value = null
    }

    // Backup and restore

    fun saveBackup(uri: Uri) {
        _backup.value = BackupState.Working
        viewModelScope.launch {
            var written = false
            _backup.value = try {
                val contents = backups.export(settings.defaultRestSeconds.value, settings.themeMode.value, settings.hevyImportZone)
                withContext(Dispatchers.IO) {
                    val bytes = encodeBackup(contents)
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(bytes)
                    } ?: throw IllegalStateException("Couldn't create the file.")
                }
                written = true
                settings.setLastBackupAt(contents.exportedAt)
                BackupState.Saved(contents.workouts.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupTooLargeException) {
                BackupState.Failed(e.message!!)
            } catch (e: Exception) {
                BackupState.Failed("The backup couldn't be saved: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                if (!written) withContext(NonCancellable + Dispatchers.IO) {
                    // The picker already created a document, even if encoding never opened it.
                    runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }
                }
            }
        }
    }

    fun openBackup(uri: Uri) {
        _backup.value = BackupState.Working
        viewModelScope.launch {
            _backup.value = try {
                BackupState.ConfirmRestore(withContext(Dispatchers.IO) { BackupJson.read(readText(uri, BACKUP_BYTE_LIMIT, BACKUP_TOO_LARGE)) })
            } catch (e: FileTooLargeException) {
                BackupState.Failed(e.message!!)
            } catch (e: BackupFormatException) {
                BackupState.Failed(e.message ?: "This backup can't be read.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupState.Failed("This file couldn't be read: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun confirmRestore() {
        val contents = (_backup.value as? BackupState.ConfirmRestore)?.contents ?: return
        _backup.value = BackupState.Working
        viewModelScope.launch {
            _backup.value = try {
                backups.restore(contents)
                settings.restoreBackupSettings(contents.defaultRestSeconds, contents.themeMode, contents.hevyImportZone)
                BackupState.Restored(contents.workouts.size)
            } catch (e: WorkoutInProgressException) {
                BackupState.Failed(e.message!!)
            } catch (e: BackupFormatException) {
                // Its message already says nothing was changed.
                BackupState.Failed(e.message ?: "This backup can't be read.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupState.Failed("The restore failed and nothing was changed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun dismissBackup() {
        if (_backup.value is BackupState.Working) return
        _backup.value = null
    }
}
