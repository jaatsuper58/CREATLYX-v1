package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.core.designsystem.theme.LocalChattlyxColors

/** Immutable render state for one chat-list row (Section 5.3 spec). */
data class ChatListItemState(
    val name: String,
    val preview: String,
    val timeText: String,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isTyping: Boolean = false,
    val outgoingTick: DeliveryTickState? = null,
)

/**
 * Chat list row: 72 dp tall, 52 dp avatar, one-line name/preview, time,
 * unread pill, mute/pin affordances, delivery tick for the outgoing last
 * message, accent-coloured "typing…" preview.
 */
@Composable
fun ChatListItem(
    state: ChatListItemState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chattlyx = LocalChattlyxColors.current

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_DP.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = state.name, size = AVATAR_SIZE_DP.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.unreadCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            chattlyx.textSecondary
                        },
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.outgoingTick != null) {
                        DeliveryTicks(state = state.outgoingTick)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = state.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (state.isTyping) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            chattlyx.textSecondary
                        },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state.isPinned) {
                        Icon(
                            imageVector = ChattlyxIcons.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = chattlyx.textSecondary,
                        )
                    }
                    if (state.isMuted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = ChattlyxIcons.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = chattlyx.textSecondary,
                        )
                    }
                    if (state.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        UnreadPill(count = state.unreadCount)
                    }
                }
            }
        }
    }
}

const val ROW_HEIGHT_DP = 72
const val AVATAR_SIZE_DP = 52

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL", locale = "ar")
@Composable
private fun ChatListItemPreview() {
    ChattlyxTheme {
        Column {
            ChatListItem(
                state = ChatListItemState(
                    name = "Asha Verma",
                    preview = "Yes! See you at 8 👍",
                    timeText = "20:14",
                    unreadCount = 3,
                    outgoingTick = DeliveryTickState.READ,
                ),
                onClick = {},
            )
            ChatListItem(
                state = ChatListItemState(
                    name = "Family",
                    preview = "Maa: Dinner is ready",
                    timeText = "18:02",
                    isMuted = true,
                    isPinned = true,
                ),
                onClick = {},
            )
            ChatListItem(
                state = ChatListItemState(
                    name = "Rahul",
                    preview = "",
                    timeText = "now",
                    isTyping = true,
                ),
                onClick = {},
            )
        }
    }
}
