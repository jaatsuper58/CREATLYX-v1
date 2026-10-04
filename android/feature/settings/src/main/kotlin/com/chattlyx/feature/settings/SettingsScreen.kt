package com.chattlyx.feature.settings

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/**
 * Settings home (S35, Section 4.10). Rows navigate to their screens as they
 * land across Phases 1/6; the list and copy are final.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
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
            onClick = {},
        )
        SettingsRow(
            title = stringResource(R.string.settings_privacy),
            summary = stringResource(R.string.settings_privacy_summary),
            onClick = {},
        )
        SettingsRow(
            title = stringResource(R.string.settings_notifications),
            summary = stringResource(R.string.settings_notifications_summary),
            onClick = {},
        )
        SettingsRow(
            title = stringResource(R.string.settings_storage),
            summary = stringResource(R.string.settings_storage_summary),
            onClick = {},
        )
        SettingsRow(
            title = stringResource(R.string.settings_appearance),
            summary = stringResource(R.string.settings_appearance_summary),
            onClick = {},
        )
        SettingsRow(
            title = stringResource(R.string.settings_help),
            summary = stringResource(R.string.settings_help_summary),
            onClick = {},
        )
        SettingsRow(
            title = stringResource(R.string.settings_about),
            summary = stringResource(R.string.settings_about_summary),
            onClick = {},
        )
    }
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
        SettingsScreen()
    }
}
