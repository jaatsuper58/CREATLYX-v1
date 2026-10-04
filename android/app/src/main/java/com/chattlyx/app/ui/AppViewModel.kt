package com.chattlyx.app.ui

import androidx.lifecycle.ViewModel
import com.chattlyx.core.datastore.AppearanceSettings
import com.chattlyx.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Exposes appearance settings that drive the global theme. */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val appearance: Flow<AppearanceSettings> = settingsRepository.appearance
}
