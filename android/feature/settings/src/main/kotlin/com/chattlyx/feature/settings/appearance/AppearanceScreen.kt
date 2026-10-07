package com.chattlyx.feature.settings.appearance

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.datastore.SettingsRepository
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.core.designsystem.theme.ChattlyxThemeMode
import com.chattlyx.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** SET (Section 4.10): appearance choices, persisted via DataStore. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val appearance = settingsRepository.appearance

    fun setThemeMode(mode: ChattlyxThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReduceMotion(enabled) }
    }
}

/**
 * Appearance settings. Every control writes straight to DataStore, which
 * AppRoot already observes to drive the global ChattlyX theme.
 */
@Composable
fun AppearanceScreen(
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val appearance by viewModel.appearance
        .collectAsStateWithLifecycle(
            initialValue = com.chattlyx.core.datastore.AppearanceSettings(),
        )
    val dynamicColorAvailable =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    AppearanceContent(
        themeMode = appearance.themeMode,
        dynamicColor = appearance.dynamicColor,
        reduceMotion = appearance.reduceMotion,
        dynamicColorAvailable = dynamicColorAvailable,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onReduceMotionChange = viewModel::setReduceMotion,
        modifier = modifier,
    )
}

/** Stateless body so previews don't need the Hilt graph. */
@Composable
internal fun AppearanceContent(
    themeMode: ChattlyxThemeMode,
    dynamicColor: Boolean,
    reduceMotion: Boolean,
    dynamicColorAvailable: Boolean,
    onThemeModeChange: (ChattlyxThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Text(
            text = stringResource(R.string.settings_appearance_theme_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ChattlyxThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onThemeModeChange(mode) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                )
                Text(
                    text = stringResource(themeModeLabel(mode)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (dynamicColorAvailable) {
            SwitchRow(
                title = stringResource(R.string.settings_appearance_dynamic_color),
                summary = stringResource(R.string.settings_appearance_dynamic_color_summary),
                checked = dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )
        }
        SwitchRow(
            title = stringResource(R.string.settings_appearance_reduce_motion),
            summary = stringResource(R.string.settings_appearance_reduce_motion_summary),
            checked = reduceMotion,
            onCheckedChange = onReduceMotionChange,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        HorizontalDivider()
    }
}

private fun themeModeLabel(mode: ChattlyxThemeMode): Int = when (mode) {
    ChattlyxThemeMode.SYSTEM -> R.string.settings_appearance_theme_system
    ChattlyxThemeMode.LIGHT -> R.string.settings_appearance_theme_light
    ChattlyxThemeMode.DARK -> R.string.settings_appearance_theme_dark
    ChattlyxThemeMode.AMOLED -> R.string.settings_appearance_theme_amoled
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", fontScale = 1.5f)
@Composable
private fun AppearanceScreenPreview() {
    ChattlyxTheme {
        AppearanceContent(
            themeMode = ChattlyxThemeMode.SYSTEM,
            dynamicColor = false,
            reduceMotion = true,
            dynamicColorAvailable = true,
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onReduceMotionChange = {},
        )
    }
}
