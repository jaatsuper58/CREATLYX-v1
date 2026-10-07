package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.core.designsystem.theme.LocalChattlyxColors

/** Button variants used across onboarding, settings and empty states. */
enum class ChattlyxButtonVariant { FILLED, GRADIENT, TONAL, OUTLINED, TEXT }

/**
 * Brand button. GRADIENT renders the outgoing-bubble gradient (Section 5.5)
 * with white text — used for primary actions such as "Invite friends".
 */
@Composable
fun ChattlyxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ChattlyxButtonVariant = ChattlyxButtonVariant.FILLED,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            Text(text)
        }
    }

    when (variant) {
        ChattlyxButtonVariant.FILLED -> Button(
            onClick = onClick,
            modifier = buttonModifier(modifier),
            enabled = enabled,
            content = content,
        )

        ChattlyxButtonVariant.GRADIENT -> GradientButton(
            onClick = onClick,
            modifier = buttonModifier(modifier),
            enabled = enabled,
            content = content,
        )

        ChattlyxButtonVariant.TONAL -> Button(
            onClick = onClick,
            modifier = buttonModifier(modifier),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            content = content,
        )

        ChattlyxButtonVariant.OUTLINED -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier(modifier),
            enabled = enabled,
            content = content,
        )

        ChattlyxButtonVariant.TEXT -> TextButton(
            onClick = onClick,
            modifier = buttonModifier(modifier),
            enabled = enabled,
            content = content,
        )
    }
}

private fun buttonModifier(modifier: Modifier): Modifier =
    modifier.defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)

@Composable
private fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val chattlyx = LocalChattlyxColors.current
    val brush = Brush.linearGradient(
        listOf(chattlyx.bubbleOutgoingStart, chattlyx.bubbleOutgoingEnd),
    )

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            contentColor = Color.White,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = brush, shape = MaterialTheme.shapes.medium),
                )
                Row(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
                        .padding(ButtonDefaults.ContentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    content()
                }
            }
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChattlyxButtonPreview() {
    ChattlyxTheme {
        Column {
            ChattlyxButton(text = "Continue", onClick = {})
            ChattlyxButton(
                text = "Invite friends",
                onClick = {},
                variant = ChattlyxButtonVariant.GRADIENT,
            )
            ChattlyxButton(text = "Not now", onClick = {}, variant = ChattlyxButtonVariant.TEXT)
            ChattlyxButton(text = "Sending", onClick = {}, loading = true)
        }
    }
}
