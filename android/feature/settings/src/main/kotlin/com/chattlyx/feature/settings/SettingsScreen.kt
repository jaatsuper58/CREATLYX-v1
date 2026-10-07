package com.chattlyx.feature.settings

import android.content.res.Configuration
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.feature.settings.privacy.AppLockViewModel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/**
 * Settings home (S35, Section 4.10). Rows navigate to their screens; Phase 1
 * wires Account/Devices/Delete, later phases add the remaining rows.
 */
@Composable
fun SettingsHomeScreen(
    onOpenAccount: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        SettingsRow(
            title = stringResource(R.string.settings_account),
            summary = stringResource(R.string.settings_account_summary),
            onClick = onOpenAccount,
        )
        SettingsRow(
            title = stringResource(R.string.settings_devices_title),
            summary = stringResource(R.string.settings_devices_current),
            onClick = onOpenDevices,
        )
        SettingsRow(
            title = stringResource(R.string.settings_privacy),
            summary = stringResource(R.string.settings_privacy_summary),
            onClick = onOpenPrivacy,
        )
        AppLockRow()
        SettingsRow(
            title = stringResource(R.string.settings_notifications),
            summary = stringResource(R.string.settings_notifications_summary),
            onClick = onOpenNotifications,
        )
        SettingsRow(
            title = stringResource(R.string.settings_storage),
            summary = stringResource(R.string.settings_storage_summary),
            onClick = onOpenStorage,
        )
        SettingsRow(
            title = stringResource(R.string.settings_appearance),
            summary = stringResource(R.string.settings_appearance_summary),
            onClick = onOpenAppearance,
        )
        SettingsRow(
            title = stringResource(R.string.settings_help),
            summary = stringResource(R.string.settings_help_summary),
            onClick = onOpenHelp,
        )
        SettingsRow(
            title = stringResource(R.string.settings_about),
            summary = stringResource(R.string.settings_about_summary),
            onClick = onOpenAbout,
        )
        SettingsRow(
            title = stringResource(R.string.settings_delete_title),
            summary = stringResource(R.string.settings_delete_body),
            onClick = onOpenDeleteAccount,
        )
    }
}

/**
 * AUTH-* (Phase 8): biometric/credential app-lock toggle. Enabling requires a
 * successful BiometricPrompt confirmation; the row is hidden when the device
 * offers no strong biometric or credential authenticator.
 */
@Composable
private fun AppLockRow() {
    val viewModel: AppLockViewModel = hiltViewModel()
    val enabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(context).canAuthenticate(authenticators)
        != BiometricManager.BIOMETRIC_SUCCESS
    ) {
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (enabled) {
                    viewModel.disable()
                } else {
                    authenticateForAppLock(activity) { viewModel.enable() }
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.settings_app_lock), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.settings_app_lock_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { checked ->
                if (checked) {
                    authenticateForAppLock(activity) { viewModel.enable() }
                } else {
                    viewModel.disable()
                }
            },
        )
    }
    HorizontalDivider()
}

/** Owner confirmation before arming the lock. */
private fun authenticateForAppLock(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.settings_app_lock_prompt_title))
        .setSubtitle(activity.getString(R.string.settings_app_lock_prompt_subtitle))
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .build()
    prompt.authenticate(info)
}

@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .semantics { contentDescription = title }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    ChattlyxTheme {
        SettingsHomeScreen(
            onOpenAccount = {},
            onOpenDevices = {},
            onOpenPrivacy = {},
            onOpenNotifications = {},
            onOpenStorage = {},
            onOpenAppearance = {},
            onOpenHelp = {},
            onOpenAbout = {},
            onOpenDeleteAccount = {},
        )
    }
}
