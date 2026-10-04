package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxMotion
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.core.designsystem.theme.LocalReduceMotion

/**
 * Shimmering skeleton rows for loading states. When reduce-motion is on the
 * shimmer renders as a static tint (accessibility, Section 5.5).
 */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val alpha by shimmerAlpha()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_DP.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AVATAR_SIZE_DP.dp)
                .clip(CircleShape)
                .background(skeletonColor(alpha)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(14.dp)
                    .clip(Material3SmallShape)
                    .background(skeletonColor(alpha)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(12.dp)
                    .clip(Material3SmallShape)
                    .background(skeletonColor(alpha * 0.8f)),
            )
        }
    }
}

/** A list of skeleton rows for first-load placeholders. */
@Composable
fun SkeletonList(
    rowCount: Int = 8,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        repeat(rowCount) {
            SkeletonRow()
        }
    }
}

@Composable
private fun shimmerAlpha(): androidx.compose.runtime.State<Float> {
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = if (reduceMotion) 0.6f else 0.35f,
        targetValue = if (reduceMotion) 0.6f else 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(ChattlyxMotion.LONG_MS * 4),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
}

@Composable
private fun skeletonColor(alpha: Float) =
    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

private val Material3SmallShape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SkeletonPreview() {
    ChattlyxTheme {
        SkeletonList(rowCount = 4)
    }
}
