package com.chattlyx.feature.contacts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.EmptyState
import com.chattlyx.core.designsystem.icon.ChattlyxIcons

/**
 * Contacts (CON-01/02). Phase 0 renders the empty state; opt-in sync,
 * on-ChattlyX / invite sections and discovery land in Phase 2.
 */
@Composable
fun ContactsScreen(
    modifier: Modifier = Modifier,
    onSyncContacts: () -> Unit = {},
) {
    EmptyState(
        icon = ChattlyxIcons.Contacts,
        title = stringResource(R.string.contacts_empty_title),
        message = stringResource(R.string.contacts_empty_message),
        modifier = modifier.fillMaxSize(),
        action = {
            ChattlyxButton(
                text = stringResource(R.string.contacts_empty_action),
                onClick = onSyncContacts,
                variant = ChattlyxButtonVariant.TONAL,
            )
        },
    )
}
