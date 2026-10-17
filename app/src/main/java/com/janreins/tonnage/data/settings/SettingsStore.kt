package com.janreins.tonnage.data.settings

import android.content.Context
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

    /** Kept separate from the device zone so travelling cannot change imported identities. */
    val hevyImportZone: String? get() = prefs.getString(KEY_HEVY_ZONE, null)

    fun rememberHevyImportZone(zoneId: String) {
        if (hevyImportZone == null) prefs.edit(commit = true) { putString(KEY_HEVY_ZONE, zoneId) }
    }

    companion object {
        const val DEFAULT_REST_SECONDS = 90
        val REST_OPTIONS = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300)
        private const val KEY_REST = "default_rest_seconds"
        private const val KEY_HEVY_ZONE = "hevy_import_zone"
        private const val KEY_THEME = "theme_mode"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
