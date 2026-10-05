package com.chattlyx.feature.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import com.chattlyx.domain.calls.CallSessionState

/**
 * CALL-01/02/04: full-screen call UI. Incoming rings get answer/decline;
 * outgoing shows ringing; connected shows duration + media controls.
 */
@Composable
fun InCallScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallsViewModel = hiltViewModel(),
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val videoEnabled by viewModel.videoEnabled.collectAsStateWithLifecycle()

    // CALL-01 video: show the shared video surface for any video-call state.
    val isVideoCall = when (val s = session) {
        is CallSessionState.Active -> s.media == com.chattlyx.domain.calls.CallMedia.VIDEO
        is CallSessionState.Outgoing -> s.media == com.chattlyx.domain.calls.CallMedia.VIDEO
        is CallSessionState.Incoming -> s.media == com.chattlyx.domain.calls.CallMedia.VIDEO
        CallSessionState.Idle -> false
    }

    // The session returning to Idle after being live means the call ended —
    // leave the route (never exit on first composition while state settles).
    val sawLiveCall = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(session) {
        if (session !is CallSessionState.Idle) {
            sawLiveCall.value = true
        } else if (sawLiveCall.value) {
            onExit()
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isVideoCall) {
                CallVideoSurface(
                    engine = viewModel.engine,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Spacer(Modifier.height(48.dp))

            val peer = when (val s = session) {
                is CallSessionState.Active -> s.peerAccountId
                is CallSessionState.Incoming -> s.peerAccountId
                is CallSessionState.Outgoing -> s.peerAccountId
                CallSessionState.Idle -> ""
            }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ChattlyxIcons.Call,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = viewModel.displayNameFor(peer).ifBlank {
                    stringResource(R.string.call_in_call)
                },
                style = MaterialTheme.typography.headlineSmall,
            )

            val statusText = when (val s = session) {
                is CallSessionState.Incoming -> stringResource(R.string.call_incoming)
                is CallSessionState.Outgoing -> stringResource(R.string.call_ringing)
                is CallSessionState.Active -> formatDuration(System.currentTimeMillis() - s.startedAt)
                CallSessionState.Idle -> ""
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.weight(1f))

            when (val s = session) {
                is CallSessionState.Incoming -> IncomingControls(
                    onAnswer = viewModel::accept,
                    onDecline = viewModel::decline,
                )
                CallSessionState.Idle,
                is CallSessionState.Outgoing,
                is CallSessionState.Active,
                -> ActiveControls(
                    canToggleMedia = session is CallSessionState.Active,
                    muted = muted,
                    videoEnabled = videoEnabled,
                    onToggleMute = viewModel::toggleMute,
                    onToggleVideo = viewModel::toggleVideo,
                    onSpeaker = viewModel::setSpeakerphone,
                    onEnd = viewModel::hangUp,
                )
            }
            }
        }
    }
}

/**
 * CALL-01 video (Phase 7): a WebRTC [org.webrtc.SurfaceViewRenderer] fed by
 * the call engine. Attached on composition, released on disposal.
 */
@Composable
private fun CallVideoSurface(
    engine: com.chattlyx.core.rtc.CallEngine,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            org.webrtc.SurfaceViewRenderer(ctx).apply {
                init(engine.eglContext, null)
                setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                engine.attachVideoRenderer(this, remote = true)
            }
        },
        onRelease = { view ->
            engine.detachVideoRenderer(view)
            view.release()
        },
    )
}

@Composable
private fun IncomingControls(onAnswer: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallControlButton(
            icon = ChattlyxIcons.Stop,
            label = stringResource(R.string.call_decline),
            tint = Color.White,
            background = MaterialTheme.colorScheme.error,
            onClick = onDecline,
        )
        CallControlButton(
            icon = ChattlyxIcons.Call,
            label = stringResource(R.string.call_answer),
            tint = Color.White,
            background = Color(0xFF2E7D32),
            onClick = onAnswer,
        )
    }
}

@Composable
private fun ActiveControls(
    canToggleMedia: Boolean,
    muted: Boolean,
    videoEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onSpeaker: (Boolean) -> Unit,
    onEnd: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallControlButton(
                icon = ChattlyxIcons.Mic,
                label = stringResource(if (muted) R.string.call_unmute else R.string.call_mute),
                tint = if (muted) Color.White else MaterialTheme.colorScheme.onSurface,
                background = if (muted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                onClick = onToggleMute,
                enabled = canToggleMedia,
            )
            CallControlButton(
                icon = ChattlyxIcons.Image,
                label = stringResource(if (videoEnabled) R.string.call_video_on else R.string.call_video_off),
                tint = if (videoEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                background = if (videoEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                onClick = onToggleVideo,
                enabled = canToggleMedia,
            )
            CallControlButton(
                icon = ChattlyxIcons.Play,
                label = stringResource(R.string.call_speaker),
                tint = MaterialTheme.colorScheme.onSurface,
                background = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onSpeaker(true) },
                enabled = canToggleMedia,
            )
        }
        Spacer(Modifier.height(24.dp))
        CallControlButton(
            icon = ChattlyxIcons.Stop,
            label = stringResource(R.string.call_end),
            tint = Color.White,
            background = MaterialTheme.colorScheme.error,
            onClick = onEnd,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(background),
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun formatDuration(elapsedMs: Long): String {
    val total = (elapsedMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(java.util.Locale.ROOT, total / 60, total % 60)
}
