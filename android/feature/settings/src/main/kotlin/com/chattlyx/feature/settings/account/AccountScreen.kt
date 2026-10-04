package com.chattlyx.feature.settings.account

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.core.designsystem.component.Avatar
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.ChattlyxTextField
import com.chattlyx.core.designsystem.component.SkeletonRow
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.feature.settings.R

/** S36: account profile editing + sign out. */
@Composable
fun AccountScreen(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.settings_account_saved)

    LaunchedEffect(state.savedFlash) {
        if (state.savedFlash) {
            snackbar.showSnackbar(savedText)
            viewModel.dismissSavedFlash()
        }
    }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
    }

    AccountContent(
        state = state,
        snackbarHostState = snackbar,
        onNameChange = viewModel::onNameChange,
        onUsernameChange = viewModel::onUsernameChange,
        onAboutChange = viewModel::onAboutChange,
        onSave = viewModel::save,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@Composable
internal fun AccountContent(
    state: AccountState,
    snackbarHostState: SnackbarHostState,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onAboutChange: (String) -> Unit,
    onSave: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_account_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            if (state.loading) {
                SkeletonRow(modifier = Modifier.fillMaxWidth().height(220.dp))
            } else {
                Avatar(name = state.displayName.ifBlank { "?" }, size = 72.dp)

                ChattlyxTextField(
                    value = state.displayName,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.settings_account_name_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                ChattlyxTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    label = stringResource(R.string.settings_account_username_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                ChattlyxTextField(
                    value = state.about,
                    onValueChange = onAboutChange,
                    label = stringResource(R.string.settings_account_about_label),
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.errorRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                ChattlyxButton(
                    text = stringResource(R.string.settings_account_save),
                    onClick = onSave,
                    loading = state.saving,
                    enabled = state.displayName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                )

                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onSignOut) {
                        Text(
                            stringResource(R.string.settings_account_sign_out),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            SnackbarHost(snackbarHostState)
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, fontScale = 1.6f)
@Preview(name = "RTL", showBackground = true, locale = "ar")
@Composable
private fun AccountContentPreview() {
    ChattlyxTheme {
        AccountContent(
            state = AccountState(loading = false, displayName = "Aarav", username = "aarav", about = "Hi!"),
            snackbarHostState = SnackbarHostState(),
            onNameChange = {},
            onUsernameChange = {},
            onAboutChange = {},
            onSave = {},
            onSignOut = {},
        )
    }
}
