package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.BubbleGroupedShape
import com.chattlyx.core.designsystem.theme.BubbleIncomingShape
import com.chattlyx.core.designsystem.theme.BubbleOutgoingShape
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.core.designsystem.theme.LocalChattlyxColors

/** Bubble direction drives background, shape and alignment (Section 5.3). */
enum class BubbleDirection { INCOMING, OUTGOING }

/**
 * Chat bubble container. Max width is 78% of the screen; the tail corner is
 * 4 dp on the sender edge for the last bubble of a group, full rounding for
 * grouped predecessors (Section 5.3).
 */
@Composable
fun MessageBubble(
    direction: BubbleDirection,
    modifier: Modifier = Modifier,
    showTail: Boolean = true,
    content: @Composable () -> Unit,
) {
    val chattlyx = LocalChattlyxColors.current

    val background: Modifier
    val textColor: Color
    val shape = when (direction) {
        BubbleDirection.INCOMING -> if (showTail) BubbleIncomingShape else BubbleGroupedShape
        BubbleDirection.OUTGOING -> if (showTail) BubbleOutgoingShape else BubbleGroupedShape
    }

    when (direction) {
        BubbleDirection.INCOMING -> {
            background = Modifier.background(
                color = chattlyx.bubbleIncoming,
                shape = shape,
            )
            textColor = MaterialTheme.colorScheme.onSurface
        }

        BubbleDirection.OUTGOING -> {
            background = Modifier.background(
                brush = Brush.linearGradient(
                    listOf(chattlyx.bubbleOutgoingStart, chattlyx.bubbleOutgoingEnd),
                ),
                shape = shape,
            )
            textColor = Color.White
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (direction == BubbleDirection.OUTGOING) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        },
    ) {
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = textColor)) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth * MAX_BUBBLE_WIDTH_FRACTION)
                    .then(background)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                content()
            }
        }
    }
}

/** Bubble max width as a fraction of the available width (Section 5.3). */
const val MAX_BUBBLE_WIDTH_FRACTION = 0.78f

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL", locale = "ar")
@Composable
private fun MessageBubblePreview() {
    ChattlyxTheme {
        Column {
            MessageBubble(direction = BubbleDirection.INCOMING) {
                Text("Are we still on for tonight?")
            }
            MessageBubble(direction = BubbleDirection.OUTGOING) {
                Text("Yes! See you at 8 👍")
            }
        }
    }
}
