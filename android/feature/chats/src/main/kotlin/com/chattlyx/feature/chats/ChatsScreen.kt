package com.chattlyx.feature.chats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.EmptyState
import com.chattlyx.core.designsystem.icon.ChattlyxIcons

/**
 * Chat list (MSG-08). Phase 0 renders the empty state; the live list,
 * filters, pinning and archive land in Phase 2.
 */
@Composable
fun ChatsScreen(
    modifier: Modifier = Modifier,
    onInvite: () -> Unit = {},
) {
    EmptyState(
        icon = ChattlyxIcons.Chat,
        title = stringResource(R.string.chats_empty_title),
        message = stringResource(R.string.chats_empty_message),
        modifier = modifier.fillMaxSize(),
        action = {
            ChattlyxButton(
                text = stringResource(R.string.chats_empty_action),
                onClick = onInvite,
                variant = ChattlyxButtonVariant.GRADIENT,
            )
        },
    )
}
