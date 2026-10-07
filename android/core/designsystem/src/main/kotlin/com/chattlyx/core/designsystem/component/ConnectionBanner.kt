package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.core.designsystem.theme.LocalChattlyxColors

/** Severity of a system banner (offline, connecting, error). */
enum class BannerSeverity { INFO, WARNING, ERROR }

/**
 * Full-width status banner shown at the top of screens (Section 5.3
 * "Waiting for network…", error and reconnect states).
 */
@Composable
fun ConnectionBanner(
    text: String,
    severity: BannerSeverity,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val chattlyx = LocalChattlyxColors.current
    val background = when (severity) {
        BannerSeverity.INFO -> MaterialTheme.colorScheme.surfaceVariant
        BannerSeverity.WARNING -> chattlyx.warning.copy(alpha = 0.15f)
        BannerSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val foreground = when (severity) {
        BannerSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        BannerSeverity.WARNING -> MaterialTheme.colorScheme.onSurface
        BannerSeverity.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = foreground,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = foreground)
            }
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectionBannerPreview() {
    ChattlyxTheme {
        androidx.compose.foundation.layout.Column {
            ConnectionBanner(text = "Waiting for network…", severity = BannerSeverity.WARNING, loading = true)
            ConnectionBanner(text = "Back online", severity = BannerSeverity.INFO)
            ConnectionBanner(text = "Couldn’t send", severity = BannerSeverity.ERROR, actionLabel = "Retry", onAction = {})
        }
    }
}
