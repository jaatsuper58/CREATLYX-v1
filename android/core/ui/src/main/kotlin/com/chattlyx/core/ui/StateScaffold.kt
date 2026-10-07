package com.chattlyx.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ConnectionBanner
import com.chattlyx.core.designsystem.component.BannerSeverity
import com.chattlyx.core.designsystem.component.EmptyState
import com.chattlyx.core.designsystem.component.SkeletonList
import com.chattlyx.core.designsystem.icon.ChattlyxIcons

/**
 * Renders the four mandatory screen states. Feature screens supply their own
 * empty-state copy/action; this scaffold owns loading/error plumbing.
 */
@Composable
fun <T> StateScaffold(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = ChattlyxIcons.Chat,
    emptyTitle: String = "",
    emptyMessage: String = "",
    errorMessage: String = "Something went wrong. Please try again.",
    retryLabel: String = "Retry",
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            UiState.Loading -> SkeletonList()

            is UiState.Content -> content(state.data)

            is UiState.Error -> androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = errorMessage, style = MaterialTheme.typography.bodyLarge)
                if (state.retryable && onRetry != null) {
                    ChattlyxButton(text = retryLabel, onClick = onRetry)
                }
            }

            UiState.Empty -> EmptyState(
                icon = emptyIcon,
                title = emptyTitle,
                message = emptyMessage,
            )
        }
    }
}

/** Convenience: offline banner slot shown above any content. */
@Composable
fun OfflineBanner(visible: Boolean, text: String) {
    if (visible) {
        ConnectionBanner(text = text, severity = BannerSeverity.WARNING, loading = true)
    }
}
