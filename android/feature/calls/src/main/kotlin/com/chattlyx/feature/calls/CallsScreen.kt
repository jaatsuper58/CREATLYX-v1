package com.chattlyx.feature.calls

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.domain.calls.CallDirection
import com.chattlyx.domain.calls.CallLogEntry
import com.chattlyx.domain.calls.CallMedia
import java.text.DateFormat
import java.util.Date

/**
 * CALL-05 call log. Tapping a row places a call back to that peer.
 */
@Composable
fun CallsScreen(
    onOpenCall: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CallsViewModel = hiltViewModel(),
) {
    val log by viewModel.callLog.collectAsStateWithLifecycle()
    val placed by viewModel.callPlaced.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(placed) {
        if (placed) {
            viewModel.consumeCallPlaced()
            onOpenCall()
        }
    }

    CallsContent(
        entries = log,
        onCallPeer = { peer -> viewModel.call(peer, CallMedia.AUDIO) },
        modifier = modifier,
    )
}

@Composable
internal fun CallsContent(
    entries: List<CallLogEntry>,
    onCallPeer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            com.chattlyx.core.designsystem.component.EmptyState(
                icon = ChattlyxIcons.Call,
                title = stringResource(R.string.calls_empty_title),
                message = stringResource(R.string.calls_empty_message),
                modifier = Modifier.fillMaxSize(),
            )
            return@Surface
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries.size) { index ->
                CallLogRow(entry = entries[index], onCall = { onCallPeer(entries[index].peerAccountId) })
            }
        }
    }
}

@Composable
private fun CallLogRow(entry: CallLogEntry, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val directionIcon = when (entry.direction) {
            CallDirection.MISSED -> ChattlyxIcons.Call
            CallDirection.INCOMING -> ChattlyxIcons.Call
            CallDirection.OUTGOING -> ChattlyxIcons.Call
        }
        Icon(
            imageVector = directionIcon,
            contentDescription = null,
            tint = if (entry.direction == CallDirection.MISSED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(24.dp),
        )
        val directionText = when (entry.direction) {
            CallDirection.INCOMING -> stringResource(R.string.calls_direction_incoming)
            CallDirection.OUTGOING -> stringResource(R.string.calls_direction_outgoing)
            CallDirection.MISSED -> stringResource(R.string.calls_direction_missed)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.peerAccountId.take(8),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$directionText · ${entry.durationLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatTime(entry.startedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onCall) {
            Icon(
                imageVector = ChattlyxIcons.Call,
                contentDescription = stringResource(R.string.calls_call_back),
            )
        }
    }
}

private fun CallLogEntry.durationLabel(): String = when {
    durationMs <= 0 -> "0:00"
    else -> {
        val total = durationMs / 1000
        "%d:%02d".format(java.util.Locale.ROOT, total / 60, total % 60)
    }
}

private fun formatTime(millis: Long): String =
    if (millis <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CallsEmptyPreview() {
    ChattlyxTheme {
        CallsContent(entries = emptyList(), onCallPeer = {})
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CallsListPreview() {
    ChattlyxTheme {
        CallsContent(
            entries = listOf(
                CallLogEntry("1", "abcdef12-0000-0000-0000-000000000000", CallDirection.INCOMING, CallMedia.AUDIO, 0L, 65_000),
                CallLogEntry("2", "fedcba98-0000-0000-0000-000000000000", CallDirection.MISSED, CallMedia.VIDEO, 0L, 0L),
            ),
            onCallPeer = {},
        )
    }
}
