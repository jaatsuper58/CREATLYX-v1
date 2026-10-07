package com.chattlyx.feature.settings.storage

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.feature.settings.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Storage settings (BKP-01/02). Exports the local message store to a
 * passphrase-encrypted file chosen via SAF, and restores from such a file.
 * Backups never leave the device through ChattlyX itself.
 */
@Composable
fun StorageScreen(
    modifier: Modifier = Modifier,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) scope.launch { viewModel.exportTo(uri) }
    }
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch { viewModel.restoreFrom(uri) }
    }

    StorageContent(
        state = state,
        modifier = modifier,
        onBackupPassphraseChange = viewModel::onBackupPassphraseChange,
        onRestorePassphraseChange = viewModel::onRestorePassphraseChange,
        onCreateBackup = { createDocument.launch(suggestedBackupFileName()) },
        onRestoreFromFile = { openDocument.launch(arrayOf("application/octet-stream", "*/*")) },
        onDismissMessage = viewModel::dismissMessage,
    )
}

/** Stateless body, kept separate so previews don't need the Hilt graph. */
@Composable
private fun StorageContent(
    state: StorageUiState,
    onBackupPassphraseChange: (String) -> Unit,
    onRestorePassphraseChange: (String) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreFromFile: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.settings_storage),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        StorageMessageBanner(message = state.message, onDismiss = onDismissMessage)

        if (state.busy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(
                    text = stringResource(R.string.settings_storage_busy),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // BKP-01: export.
        Text(
            text = stringResource(R.string.settings_storage_backup_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_storage_backup_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = state.backupPassphrase,
            onValueChange = onBackupPassphraseChange,
            label = { Text(stringResource(R.string.settings_storage_passphrase_label)) },
            supportingText = { Text(stringResource(R.string.settings_storage_passphrase_hint)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Button(
            onClick = onCreateBackup,
            enabled = !state.busy,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.settings_storage_create_backup))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // BKP-02: restore.
        Text(
            text = stringResource(R.string.settings_storage_restore_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_storage_restore_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = state.restorePassphrase,
            onValueChange = onRestorePassphraseChange,
            label = { Text(stringResource(R.string.settings_storage_passphrase_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Button(
            onClick = onRestoreFromFile,
            enabled = !state.busy,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.settings_storage_restore_button))
        }
    }
}

@Composable
private fun StorageMessageBanner(message: StorageMessage?, onDismiss: () -> Unit) {
    if (message == null) return
    val text = when (message) {
        StorageMessage.Exported -> stringResource(R.string.settings_storage_exported)
        is StorageMessage.Restored -> stringResource(
            R.string.settings_storage_restored,
            message.conversations,
            message.messages,
        )
        StorageMessage.PassphraseTooShort ->
            stringResource(R.string.settings_storage_error_passphrase_short)
        StorageMessage.WrongPassphrase ->
            stringResource(R.string.settings_storage_error_wrong_passphrase)
        StorageMessage.CorruptFile -> stringResource(R.string.settings_storage_error_corrupt)
        StorageMessage.IoFailure -> stringResource(R.string.settings_storage_error_io)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.settings_storage_dismiss))
        }
    }
}

/** Default SAF filename, e.g. chattlyx-backup-2026-10-07.chx. */
private fun suggestedBackupFileName(): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return "chattlyx-backup-$stamp.chx"
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", fontScale = 1.5f)
@Composable
private fun StorageScreenPreview() {
    ChattlyxTheme {
        StorageContent(
            state = StorageUiState(message = StorageMessage.Exported),
            onBackupPassphraseChange = {},
            onRestorePassphraseChange = {},
            onCreateBackup = {},
            onRestoreFromFile = {},
            onDismissMessage = {},
        )
    }
}
