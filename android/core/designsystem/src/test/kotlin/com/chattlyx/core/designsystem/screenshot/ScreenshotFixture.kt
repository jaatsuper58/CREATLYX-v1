package com.chattlyx.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.component.Avatar
import com.chattlyx.core.designsystem.component.BannerSeverity
import com.chattlyx.core.designsystem.component.BubbleDirection
import com.chattlyx.core.designsystem.component.ChatListItem
import com.chattlyx.core.designsystem.component.ChatListItemState
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.ChattlyxFilterChip
import com.chattlyx.core.designsystem.component.ChattlyxTextField
import com.chattlyx.core.designsystem.component.ConnectionBanner
import com.chattlyx.core.designsystem.component.DeliveryTickState
import com.chattlyx.core.designsystem.component.DeliveryTicks
import com.chattlyx.core.designsystem.component.EmptyState
import com.chattlyx.core.designsystem.component.MessageBubble
import com.chattlyx.core.designsystem.component.OtpInputField
import com.chattlyx.core.designsystem.component.SkeletonList
import com.chattlyx.core.designsystem.component.UnreadPill
import com.chattlyx.core.designsystem.icon.ChattlyxIcons

/**
 * Deterministic fixture rendering the Phase-0 component set. Screenshot
 * goldens are generated from this composable in light/dark/RTL/large-font
 * configurations (Roborazzi; see CONTRIBUTING.md).
 */
@Composable
fun ScreenshotFixture() {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChattlyxButton(text = "Continue", onClick = {})
                ChattlyxButton(text = "Invite", onClick = {}, variant = ChattlyxButtonVariant.GRADIENT)
                ChattlyxButton(text = "Later", onClick = {}, variant = ChattlyxButtonVariant.TEXT)
            }

            ChattlyxTextField(
                value = "Asha",
                onValueChange = {},
                label = "Display name",
                modifier = Modifier.fillMaxWidth(),
            )

            OtpInputField(otp = "42", onOtpChange = {}, onComplete = {})

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Avatar(name = "Asha Verma", size = 52.dp)
                Spacer(Modifier.width(12.dp))
                Avatar(name = "Rahul Kapoor", size = 40.dp)
                Spacer(Modifier.width(12.dp))
                UnreadPill(count = 3)
                Spacer(Modifier.width(6.dp))
                UnreadPill(count = 120)
                Spacer(Modifier.width(12.dp))
                DeliveryTickState.entries.forEach { tick ->
                    DeliveryTicks(state = tick)
                    Spacer(Modifier.width(4.dp))
                }
            }

            MessageBubble(direction = BubbleDirection.INCOMING) {
                Text("Are we still on for tonight?")
            }
            MessageBubble(direction = BubbleDirection.OUTGOING) {
                Text("Yes! See you at 8")
            }

            ConnectionBanner(text = "Waiting for network…", severity = BannerSeverity.WARNING, loading = true)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChattlyxFilterChip(label = "All", selected = true, onClick = {})
                ChattlyxFilterChip(label = "Unread", selected = false, onClick = {})
                ChattlyxFilterChip(label = "Groups", selected = false, onClick = {})
            }

            ChatListItem(
                state = ChatListItemState(
                    name = "Asha Verma",
                    preview = "Yes! See you at 8",
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

            SkeletonList(rowCount = 2)
        }
    }
}

/** Empty-state fixture captured separately (it fills the available height). */
@Composable
fun EmptyStateFixture() {
    Surface(modifier = Modifier.fillMaxWidth().height(360.dp)) {
        EmptyState(
            icon = ChattlyxIcons.Chat,
            title = "No chats yet",
            message = "Say hi — start a chat with someone on ChattlyX.",
            action = {
                ChattlyxButton(
                    text = "Invite friends",
                    onClick = {},
                    variant = ChattlyxButtonVariant.GRADIENT,
                )
            },
        )
    }
}
