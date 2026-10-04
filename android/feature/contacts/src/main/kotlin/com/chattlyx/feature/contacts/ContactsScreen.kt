package com.chattlyx.feature.contacts

import android.content.res.Configuration
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.core.designsystem.component.Avatar
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.EmptyState
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.domain.messaging.ContactInfo

/**
 * Contacts (CON-01..04): opt-in address-book sync, private discovery and
 * the list of people already on ChattlyX.
 */
@Composable
fun ContactsScreen(
    onOpenChat: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val known by viewModel.knownContacts.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val dialCode = runCatching {
                context.getSystemService(TelephonyManager::class.java).networkCountryIso
            }.getOrNull().orEmpty()
            val numbers = ContactNumbersReader.readE164Numbers(
                context,
                dialCodeForIso(dialCode),
            )
            viewModel.syncFromDevice(numbers)
        } else {
            viewModel.permissionDenied()
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.contacts_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            when {
                phase == ContactsPhase.LOADING -> {
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                phase == ContactsPhase.ERROR -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.contacts_sync_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    ChattlyxButton(
                        text = stringResource(R.string.contacts_retry),
                        onClick = viewModel::resetError,
                        variant = ChattlyxButtonVariant.TONAL,
                    )
                }

                known.isEmpty() -> {
                    EmptyState(
                        icon = ChattlyxIcons.Contacts,
                        title = stringResource(R.string.contacts_empty_title),
                        message = stringResource(R.string.contacts_empty_message),
                        action = {
                            ChattlyxButton(
                                text = stringResource(R.string.contacts_empty_action),
                                onClick = {
                                    permissionLauncher.launch(ContactsViewModel.CONTACTS_PERMISSION)
                                },
                                variant = ChattlyxButtonVariant.GRADIENT,
                            )
                        },
                    )
                }

                else -> {
                    if (phase == ContactsPhase.NEEDS_PERMISSION) {
                        Text(
                            text = stringResource(R.string.contacts_permission_rationale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ChattlyxButton(
                            text = stringResource(R.string.contacts_resync),
                            onClick = {
                                permissionLauncher.launch(ContactsViewModel.CONTACTS_PERMISSION)
                            },
                            variant = ChattlyxButtonVariant.TONAL,
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(known, key = { it.accountId }) { contact ->
                            ContactRow(
                                contact = contact,
                                onClick = { viewModel.openChat(contact.accountId, onOpenChat) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = contact.displayName, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = contact.displayName, style = MaterialTheme.typography.titleMedium)
            contact.username?.let { username ->
                Text(
                    text = "@$username",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun dialCodeForIso(iso: String): String = when (iso.uppercase()) {
    "US", "CA" -> "+1"
    "GB" -> "+44"
    "AE" -> "+971"
    "SA" -> "+966"
    "PK" -> "+92"
    "BD" -> "+880"
    "ES" -> "+34"
    "MX" -> "+52"
    "FR" -> "+33"
    "BR" -> "+55"
    "PT" -> "+351"
    "ID" -> "+62"
    "TR" -> "+90"
    "RU" -> "+7"
    "DE" -> "+49"
    "NG" -> "+234"
    "EG" -> "+20"
    "ZA" -> "+27"
    "SG" -> "+65"
    else -> "+91"
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, fontScale = 1.6f)
@Preview(name = "RTL", showBackground = true, locale = "ar")
@Composable
private fun ContactsEmptyPreview() {
    ChattlyxTheme {
        ContactsScreen(onOpenChat = {})
    }
}
