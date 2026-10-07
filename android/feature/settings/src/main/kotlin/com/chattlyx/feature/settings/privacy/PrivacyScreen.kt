package com.chattlyx.feature.settings.privacy

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.feature.settings.R

/**
 * Privacy overview (SET / Section 9.4): mirrors the data inventory in
 * docs/PRIVACY.md. Informational by design — the guarantees listed here are
 * enforced in code (E2EE, no analytics SDKs, on-device backups, app lock).
 */
@Composable
fun PrivacyScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.settings_privacy),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        PrivacyPoint(
            title = stringResource(R.string.settings_privacy_e2ee_title),
            body = stringResource(R.string.settings_privacy_e2ee_body),
        )
        PrivacyPoint(
            title = stringResource(R.string.settings_privacy_minimal_title),
            body = stringResource(R.string.settings_privacy_minimal_body),
        )
        PrivacyPoint(
            title = stringResource(R.string.settings_privacy_backups_title),
            body = stringResource(R.string.settings_privacy_backups_body),
        )
        PrivacyPoint(
            title = stringResource(R.string.settings_privacy_lock_title),
            body = stringResource(R.string.settings_privacy_lock_body),
        )
    }
}

@Composable
private fun PrivacyPoint(title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", fontScale = 1.5f)
@Composable
private fun PrivacyScreenPreview() {
    ChattlyxTheme {
        PrivacyScreen()
    }
}
