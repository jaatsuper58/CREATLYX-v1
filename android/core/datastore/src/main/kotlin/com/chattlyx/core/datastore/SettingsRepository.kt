package com.chattlyx.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chattlyx.core.designsystem.theme.ChattlyxThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Settings persistence backed by DataStore (Section 7.1). Phase 0 covers the
 * appearance subset that the theme needs; later phases extend this class with
 * privacy, notification and storage preferences.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val appearance: Flow<AppearanceSettings> = dataStore.data.map { prefs ->
        AppearanceSettings(
            themeMode = prefs[KEY_THEME_MODE]?.let { raw ->
                ChattlyxThemeMode.entries.firstOrNull { it.name == raw }
            } ?: ChattlyxThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            reduceMotion = prefs[KEY_REDUCE_MOTION] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ChattlyxThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_REDUCE_MOTION] = enabled }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("appearance.theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("appearance.dynamic_color")
        val KEY_REDUCE_MOTION = booleanPreferencesKey("appearance.reduce_motion")
    }
}
