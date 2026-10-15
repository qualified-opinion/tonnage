package com.janreins.tonnage.data.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _defaultRestSeconds = MutableStateFlow(prefs.getInt(KEY_REST, DEFAULT_REST_SECONDS))
    val defaultRestSeconds: StateFlow<Int> = _defaultRestSeconds.asStateFlow()

    fun setDefaultRestSeconds(seconds: Int) {
        prefs.edit { putInt(KEY_REST, seconds) }
        _defaultRestSeconds.value = seconds
    }

    companion object {
        const val DEFAULT_REST_SECONDS = 90
        val REST_OPTIONS = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300)
        private const val KEY_REST = "default_rest_seconds"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
