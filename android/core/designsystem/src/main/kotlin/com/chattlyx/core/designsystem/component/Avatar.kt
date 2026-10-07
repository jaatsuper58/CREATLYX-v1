package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/**
 * Circular avatar with deterministic initials fallback. Photo loading
 * (Coil) is wired when profiles land (Phase 1); the shape/size tokens are
 * final: 40 dp (chat header), 52 dp (chat list, Section 5.3).
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColorFor(name))
            .semantics { contentDescription = "Profile photo of $name" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = when {
                size >= 52.dp -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
        )
    }
}

/** Stable colour per contact derived from the display name hash. */
internal fun avatarColorFor(name: String): Color {
    val palette = listOf(
        Color(0xFF4F46E5),
        Color(0xFF0891B2),
        Color(0xFF7C3AED),
        Color(0xFF0D9488),
        Color(0xFFB45309),
        Color(0xFFBE185D),
    )
    val index = (name.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}

internal fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .take(2)
        .mapNotNull { word -> word.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AvatarPreview() {
    ChattlyxTheme {
        Avatar(name = "Asha Verma", size = 52.dp)
    }
}
