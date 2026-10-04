package com.chattlyx.feature.calls

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chattlyx.core.designsystem.component.EmptyState
import com.chattlyx.core.designsystem.icon.ChattlyxIcons

/**
 * Call log (CALL-05). Phase 0 renders the empty state; call entries, missed
 * call handling and call-back land in Phase 5.
 */
@Composable
fun CallsScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = ChattlyxIcons.Call,
        title = stringResource(R.string.calls_empty_title),
        message = stringResource(R.string.calls_empty_message),
        modifier = modifier.fillMaxSize(),
    )
}
