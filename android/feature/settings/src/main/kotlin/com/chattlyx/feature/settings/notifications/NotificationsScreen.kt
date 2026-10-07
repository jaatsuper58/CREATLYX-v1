package com.chattlyx.feature.settings.notifications

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.feature.settings.R

/** Localised label for a channel's importance level. */
internal data class ChannelRow(val name: String, val importanceLabel: Int)

/**
 * Notification settings (NOT-*). Lists the real channels the app has
 * registered and deep-links into the system settings where the user keeps
 * final control (Android owns notification permission).
 */
@Composable
fun NotificationsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val channels = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManagerCompat.from(context).notificationChannels.map { channel ->
                ChannelRow(
                    name = channel.name.toString(),
                    importanceLabel = importanceLabel(channel.importance),
                )
            }
        } else {
            emptyList()
        }
    }

    NotificationsContent(
        channels = channels,
        onOpenSystemSettings = {
            context.startActivity(systemNotificationSettings(context.packageName))
        },
        modifier = modifier,
    )
}

/** Stateless body so previews don't touch NotificationManagerCompat. */
@Composable
internal fun NotificationsContent(
    channels: List<ChannelRow>,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.settings_notifications),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text = stringResource(R.string.settings_notifications_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Text(
            text = stringResource(R.string.settings_notifications_channels_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (channels.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_notifications_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            channels.forEach { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = channel.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(channel.importanceLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        OutlinedButton(
            onClick = onOpenSystemSettings,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.settings_notifications_system))
        }
    }
}

private fun importanceLabel(importance: Int): Int = when {
    importance >= android.app.NotificationManager.IMPORTANCE_HIGH ->
        R.string.settings_notifications_importance_high
    importance == android.app.NotificationManager.IMPORTANCE_DEFAULT ->
        R.string.settings_notifications_importance_default
    importance == android.app.NotificationManager.IMPORTANCE_LOW ->
        R.string.settings_notifications_importance_low
    importance == android.app.NotificationManager.IMPORTANCE_MIN ->
        R.string.settings_notifications_importance_silent
    else -> R.string.settings_notifications_importance_off
}

/** App notification settings intent; API 26+ supports the per-app extra. */
private fun systemNotificationSettings(packageName: String): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsScreenPreview() {
    ChattlyxTheme {
        NotificationsContent(
            channels = listOf(
                ChannelRow("Incoming calls", R.string.settings_notifications_importance_high),
            ),
            onOpenSystemSettings = {},
        )
    }
}
