package com.chattlyx.feature.settings.about

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.feature.settings.R

/** About (SET): version metadata plus the privacy promise in one screen. */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appLabel = remember { context.applicationInfo.loadLabel(context.packageManager) }
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionCode = remember(packageInfo) { PackageInfoCompat.getLongVersionCode(packageInfo) }

    AboutContent(
        appName = appLabel.toString(),
        versionText = stringResource(
            R.string.settings_about_version,
            packageInfo.versionName ?: "",
            versionCode,
        ),
        modifier = modifier,
    )
}

/** Stateless body so previews don't need a PackageManager. */
@Composable
internal fun AboutContent(
    appName: String,
    versionText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = appName,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 32.dp),
        )
        Text(
            text = stringResource(R.string.settings_about_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = versionText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        Text(
            text = stringResource(R.string.settings_about),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_about_privacy_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", fontScale = 1.5f)
@Composable
private fun AboutScreenPreview() {
    ChattlyxTheme {
        AboutContent(appName = "ChattlyX", versionText = "Version 0.11.0 (11)")
    }
}
