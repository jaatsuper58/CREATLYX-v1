package com.chattlyx.feature.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.ChattlyxTextField
import com.chattlyx.core.designsystem.component.DeliveryTickState
import com.chattlyx.core.designsystem.component.DeliveryTicks
import com.chattlyx.core.designsystem.component.MessageBubble
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import com.chattlyx.domain.messaging.DeliveryStatus
import com.chattlyx.domain.messaging.Message
import java.text.DateFormat
import java.util.Date

/** MSG-03: one conversation — paged history, composer, receipts, typing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val peerTyping by viewModel.peerTyping.collectAsStateWithLifecycle()
    val composer by viewModel.composerText.collectAsStateWithLifecycle()
    val pagingItems = viewModel.messages.collectAsLazyPagingItems()

    DisposableEffect(Unit) {
        onDispose { viewModel.markRead() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = conversation?.peerName
                                ?: stringResource(R.string.conversation_loading),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (peerTyping) {
                            Text(
                                text = stringResource(R.string.conversation_typing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = ChattlyxIcons.Settings,
                            contentDescription = stringResource(R.string.conversation_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            val refreshing = pagingItems.loadState.refresh is LoadState.Loading

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (refreshing && pagingItems.itemCount == 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                    )
                } else {
                    // Newest first keeps keyboard-adjacent scrolling cheap;
                    // the list is read top-down by reversing visually.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(pagingItems.itemCount) { index ->
                            val message = pagingItems[index] ?: return@items
                            MessageRow(message = message)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChattlyxTextField(
                    value = composer,
                    onValueChange = viewModel::onTextChanged,
                    placeholder = stringResource(R.string.conversation_composer_hint),
                    singleLine = false,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ChattlyxButton(
                    text = stringResource(R.string.conversation_send),
                    onClick = viewModel::send,
                    variant = ChattlyxButtonVariant.FILLED,
                    enabled = composer.isNotBlank(),
                    modifier = Modifier.height(48.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageRow(message: Message, modifier: Modifier = Modifier) {
    MessageBubble(
        direction = if (message.isMine) {
            com.chattlyx.core.designsystem.component.BubbleDirection.OUTGOING
        } else {
            com.chattlyx.core.designsystem.component.BubbleDirection.INCOMING
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatTime(message.receivedAt ?: message.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (message.isMine) {
                    DeliveryTicks(state = message.status.toTickState())
                }
            }
        }
    }
}

private fun DeliveryStatus.toTickState(): DeliveryTickState = when (this) {
    DeliveryStatus.PENDING -> DeliveryTickState.PENDING
    DeliveryStatus.SENT -> DeliveryTickState.SENT
    DeliveryStatus.DELIVERED -> DeliveryTickState.DELIVERED
    DeliveryStatus.READ -> DeliveryTickState.READ
    DeliveryStatus.FAILED -> DeliveryTickState.FAILED
}

private fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
