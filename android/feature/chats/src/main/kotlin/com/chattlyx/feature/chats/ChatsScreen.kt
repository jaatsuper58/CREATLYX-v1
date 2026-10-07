package com.chattlyx.feature.chats

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val createdGroupId by viewModel.createdGroupId.collectAsStateWithLifecycle()
    val groupErrorKey by viewModel.groupErrorKey.collectAsStateWithLifecycle()
    var createDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(createdGroupId) {
        val groupId = createdGroupId ?: return@LaunchedEffect
        viewModel.consumeCreatedGroup()
        onOpenConversation("grp:$groupId")
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.chattlyx.core.designsystem.component.ChattlyxTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = stringResource(R.string.chats_search_hint),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (searchQuery.isNotBlank()) {
                androidx.compose.material3.IconButton(onClick = viewModel::clearSearch) {
                    Icon(
                        imageVector = ChattlyxIcons.Stop,
                        contentDescription = stringResource(R.string.chats_search_clear),
                    )
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            SearchResults(
                results = searchResults,
                onOpenConversation = onOpenConversation,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                ChatsContent(
                    pagingItems = pagingItems,
                    typingByConversation = typing,
                    onOpenConversation = onOpenConversation,
                    onInvite = onInvite,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.fillMaxSize())
            FloatingActionButton(
                onClick = { createDialogOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = ChattlyxIcons.Contacts,
                    contentDescription = stringResource(R.string.group_new),
                )
            }
    }

    if (createDialogOpen) {
        CreateGroupDialog(
            contacts = contacts,
            errorKey = groupErrorKey,
            onDismiss = {
                createDialogOpen = false
                viewModel.consumeGroupError()
            },
            onCreate = { name, memberIds -> viewModel.createGroup(name, memberIds) },
        )
    }
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

/** SRCH-01: combined name + message search results. */
@Composable
private fun SearchResults(
    results: com.chattlyx.feature.chats.ChatsViewModel.SearchResults,
    onOpenConversation: (String) -> Unit,
) {
    if (results.conversations.isEmpty() && results.messages.isEmpty()) {
        Text(
            text = stringResource(R.string.chats_search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results.conversations.size) { index ->
            val conversation = results.conversations[index]
            com.chattlyx.core.designsystem.component.ChatListItem(
                state = conversation.toRowState(false),
                onClick = { onOpenConversation(conversation.id) },
            )
        }
        items(results.messages.size) { index ->
            val hit = results.messages[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenConversation(hit.conversationId) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(imageVector = ChattlyxIcons.Chat, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hit.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                    Text(
                        text = formatTime(hit.sentAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** GRP-01: name + member selection over discovered contacts. */
@Composable
private fun CreateGroupDialog(
    contacts: List<com.chattlyx.domain.messaging.ContactInfo>,
    errorKey: String?,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val errorText = when (errorKey) {
        null -> null
        "validation_group_name_empty" -> stringResource(R.string.group_name_empty)
        "validation_group_name_too_long" -> stringResource(R.string.group_name_too_long)
        else -> stringResource(R.string.group_create_failed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onCreate(name, selected.toList()) }) {
                Text(stringResource(R.string.group_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.group_cancel))
            }
        },
        title = { Text(stringResource(R.string.group_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.chattlyx.core.designsystem.component.ChattlyxTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.group_name_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (contacts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.group_no_contacts),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                contacts.forEach { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selected.contains(contact.accountId)) {
                                    selected.remove(contact.accountId)
                                } else {
                                    selected.add(contact.accountId)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected.contains(contact.accountId),
                            onCheckedChange = { checked ->
                                if (checked) selected.add(contact.accountId) else selected.remove(contact.accountId)
                            },
                        )
                        Text(text = contact.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}

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
