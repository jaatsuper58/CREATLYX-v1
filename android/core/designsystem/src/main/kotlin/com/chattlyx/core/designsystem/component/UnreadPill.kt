package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/** Unread counter pill; caps display at "99+" (Section 5.3). */
@Composable
fun UnreadPill(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (count > MAX_DISPLAY_COUNT) "$MAX_DISPLAY_COUNT+" else count.toString(),
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
    )
}

const val MAX_DISPLAY_COUNT = 99

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UnreadPillPreview() {
    ChattlyxTheme {
        UnreadPill(count = 120)
    }
}
