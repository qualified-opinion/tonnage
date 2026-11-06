package com.janreins.tonnage.data.settings

import android.content.Context
import java.time.ZoneId
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark look; [SYSTEM] follows the phone's setting. */
enum class ThemeMode(val label: String) {
    SYSTEM("Same as phone"),
    LIGHT("Light"),
    DARK("Dark"),
    ;

    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

class SettingsStore internal constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _defaultRestSeconds = MutableStateFlow(prefs.getInt(KEY_REST, DEFAULT_REST_SECONDS))
    val defaultRestSeconds: StateFlow<Int> = _defaultRestSeconds.asStateFlow()

    fun setDefaultRestSeconds(seconds: Int) {
        prefs.edit { putInt(KEY_REST, seconds) }
        _defaultRestSeconds.value = seconds
    }

    private val _themeMode = MutableStateFlow(ThemeMode.fromStored(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    private val _lastBackupAt = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 })
    /** When the last backup file was fully written, or null if there hasn't been one. */
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

    fun setLastBackupAt(timeMs: Long) {
        prefs.edit { putLong(KEY_LAST_BACKUP, timeMs) }
        _lastBackupAt.value = timeMs
    }

    private val _autoBackupFolder = MutableStateFlow(prefs.getString(KEY_AUTO_FOLDER, null))
    /** The folder (a document tree URI) automatic backups go to; null when they're off. */
    val autoBackupFolder: StateFlow<String?> = _autoBackupFolder.asStateFlow()

    fun setAutoBackupFolder(treeUri: String?) {
        prefs.edit { putString(KEY_AUTO_FOLDER, treeUri).remove(KEY_AUTO_ERROR) }
        _autoBackupFolder.value = treeUri
        _autoBackupError.value = null
    }

    private val _autoBackupError = MutableStateFlow(prefs.getString(KEY_AUTO_ERROR, null))
    /** Why the last automatic backup failed; null after a success. */
    val autoBackupError: StateFlow<String?> = _autoBackupError.asStateFlow()

    fun setAutoBackupError(message: String?) {
        prefs.edit { putString(KEY_AUTO_ERROR, message) }
        _autoBackupError.value = message
    }

    /** Kept separate from the device zone so travelling cannot change imported identities. */
    val hevyImportZone: String? get() = prefs.getString(KEY_HEVY_ZONE, null)

    fun rememberHevyImportZone(zoneId: String) {
        if (hevyImportZone == null) prefs.edit(commit = true) { putString(KEY_HEVY_ZONE, zoneId) }
    }

    /** Old backups leave the theme alone; an established import zone is never overwritten. */
    fun restoreBackupSettings(restSeconds: Int, theme: ThemeMode?, importZone: String?) {
        setDefaultRestSeconds(restSeconds)
        theme?.let(::setThemeMode)
        if (hevyImportZone == null && importZone != null) {
            runCatching { ZoneId.of(importZone) }.getOrNull()?.id?.let(::rememberHevyImportZone)
        }
    }

    internal val librarySeedVersion: Int get() = prefs.getInt(KEY_LIBRARY_VERSION, -1)

    internal fun setLibrarySeedVersion(versionCode: Int) {
        prefs.edit(commit = true) { putInt(KEY_LIBRARY_VERSION, versionCode) }
    }

    companion object {
        const val DEFAULT_REST_SECONDS = 90
        val REST_OPTIONS = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300)
        private const val KEY_REST = "default_rest_seconds"
        private const val KEY_HEVY_ZONE = "hevy_import_zone"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_LIBRARY_VERSION = "library_seed_version"
        private const val KEY_AUTO_FOLDER = "auto_backup_folder"
        private const val KEY_AUTO_ERROR = "auto_backup_error"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
