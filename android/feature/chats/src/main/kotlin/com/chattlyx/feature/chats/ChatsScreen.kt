package com.chattlyx.feature.chats

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.chattlyx.core.designsystem.component.ChatListItem
import com.chattlyx.core.designsystem.component.ChatListItemState
import com.chattlyx.core.designsystem.component.EmptyState
import com.chattlyx.core.designsystem.component.SkeletonRow
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.domain.messaging.Conversation
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.flowOf

/** MSG-08: the chat list. */
@Composable
fun ChatsScreen(
    onOpenConversation: (String) -> Unit,
    onInvite: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val pagingItems = viewModel.conversations.collectAsLazyPagingItems()
    val typing by viewModel.typingByConversation.collectAsStateWithLifecycle()

    ChatsContent(
        pagingItems = pagingItems,
        typingByConversation = typing,
        onOpenConversation = onOpenConversation,
        onInvite = onInvite,
        modifier = modifier,
    )
}

@Composable
internal fun ChatsContent(
    pagingItems: LazyPagingItems<Conversation>,
    typingByConversation: Map<String, Boolean>,
    onOpenConversation: (String) -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        val loading = pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0

        if (loading) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(6) { SkeletonRow() }
            }
            return@Surface
        }

        if (pagingItems.itemCount == 0) {
            EmptyState(
                icon = ChattlyxIcons.Chat,
                title = stringResource(R.string.chats_empty_title),
                message = stringResource(R.string.chats_empty_message),
                action = {
                    com.chattlyx.core.designsystem.component.ChattlyxButton(
                        text = stringResource(R.string.chats_empty_action),
                        onClick = onInvite,
                        variant = com.chattlyx.core.designsystem.component.ChattlyxButtonVariant.GRADIENT,
                    )
                },
            )
            return@Surface
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pagingItems.itemCount) { index ->
                val conversation = pagingItems[index] ?: return@items
                val isTyping = typingByConversation[conversation.id] == true
                ChatListItem(
                    state = conversation.toRowState(isTyping),
                    onClick = { onOpenConversation(conversation.id) },
                )
            }
        }
    }
}

private fun Conversation.toRowState(isTyping: Boolean) = ChatListItemState(
    name = peerName.ifBlank { peerAccountId.take(8) },
    preview = lastMessageText,
    timeText = formatTime(lastMessageAt),
    unreadCount = unreadCount,
    isPinned = pinned,
    isTyping = isTyping,
)

private fun formatTime(millis: Long): String =
    if (millis <= 0) "" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, fontScale = 1.6f)
@Preview(name = "RTL", showBackground = true, locale = "ar")
@Composable
private fun ChatsEmptyPreview() {
    ChattlyxTheme {
        ChatsContent(
            pagingItems = flowOf(PagingData.empty<Conversation>()).collectAsLazyPagingItems(),
            typingByConversation = emptyMap(),
            onOpenConversation = {},
            onInvite = {},
        )
    }
}
