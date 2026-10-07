package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/** Filter chip used by chat-list filters (All · Unread · Groups · Favourites). */
@Composable
fun ChattlyxFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChattlyxFilterChipPreview() {
    ChattlyxTheme {
        Row {
            ChattlyxFilterChip(label = "All", selected = true, onClick = {})
            ChattlyxFilterChip(label = "Unread", selected = false, onClick = {})
        }
    }
}
