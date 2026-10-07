package com.chattlyx.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import com.chattlyx.domain.messaging.AttachmentInfo
import com.chattlyx.domain.messaging.AttachmentKind
import com.chattlyx.domain.messaging.AttachmentState
import com.chattlyx.domain.messaging.Message
import java.io.File
import java.util.Locale

/**
 * MED-01/03/04 bubble body: renders images inline, voice notes with a local
 * player, video/document rows with open/download affordances.
 */
@Composable
fun AttachmentBubble(
    message: Message,
    localFile: File?,
    onDownload: (Message) -> Unit,
    onOpenImage: (Message) -> Unit,
    onOpenFile: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachment = message.attachment ?: return
    when (attachment.kind) {
        AttachmentKind.IMAGE -> ImageBubble(
            attachment = attachment,
            localFile = localFile,
            onDownload = { onDownload(message) },
            onOpen = { onOpenImage(message) },
            modifier = modifier,
        )
        AttachmentKind.VOICE -> VoiceBubble(
            attachment = attachment,
            localFile = localFile,
            onDownload = { onDownload(message) },
            modifier = modifier,
        )
        AttachmentKind.VIDEO, AttachmentKind.FILE -> DocumentRow(
            attachment = attachment,
            localFile = localFile,
            onDownload = { onDownload(message) },
            onOpen = { onOpenFile(message) },
            modifier = modifier,
        )
    }
}

@Composable
private fun ImageBubble(
    attachment: AttachmentInfo,
    localFile: File?,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ratio = attachment.ratioOrNull()
    val boxModifier = modifier
        .widthIn(max = 260.dp)
        .then(ratio?.let { Modifier.aspectRatio(it) } ?: Modifier.height(180.dp))
        .clip(RoundedCornerShape(12.dp))

    when {
        attachment.state == AttachmentState.READY && localFile != null -> {
            AsyncImage(
                model = localFile,
                contentDescription = stringResource(R.string.attachment_image_label),
                contentScale = ContentScale.Crop,
                modifier = boxModifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen),
            )
        }
        attachment.state == AttachmentState.DOWNLOADING -> {
            Box(modifier = boxModifier.background(Color(0x33888888)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
        else -> {
            Box(
                modifier = boxModifier
                    .background(Color(0x33888888))
                    .clickable(onClick = onDownload),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (attachment.state == AttachmentState.FAILED) {
                        ChattlyxIcons.Stop
                    } else {
                        ChattlyxIcons.Download
                    },
                    contentDescription = stringResource(R.string.attachment_download),
                )
            }
        }
    }
}

@Composable
private fun VoiceBubble(
    attachment: AttachmentInfo,
    localFile: File?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachment.state == AttachmentState.READY && localFile != null) {
        VoicePlayer(file = localFile, durationMs = attachment.durationMs, modifier = modifier)
    } else {
        TransferRow(
            attachment = attachment,
            icon = ChattlyxIcons.Mic,
            label = stringResource(R.string.attachment_voice_label),
            onDownload = onDownload,
            modifier = modifier,
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VoicePlayer(file: File, durationMs: Int?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            prepare()
        }
    }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playing = false
            }
        }
        player.addListener(listener)
        onDispose { player.release() }
    }

    Row(
        modifier = modifier
            .widthIn(min = 180.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = {
            if (playing) {
                player.pause()
            } else {
                player.play()
            }
        }) {
            Icon(
                imageVector = if (playing) ChattlyxIcons.Stop else ChattlyxIcons.Play,
                contentDescription = stringResource(
                    if (playing) R.string.attachment_stop else R.string.attachment_play,
                ),
            )
        }
        Column {
            Text(
                text = stringResource(R.string.attachment_voice_label),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (durationMs != null && durationMs > 0) {
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DocumentRow(
    attachment: AttachmentInfo,
    localFile: File?,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TransferRow(
        attachment = attachment,
        icon = if (attachment.kind == AttachmentKind.VIDEO) {
            ChattlyxIcons.Play
        } else {
            ChattlyxIcons.FileDoc
        },
        label = attachment.fileName
            ?: stringResource(
                if (attachment.kind == AttachmentKind.VIDEO) {
                    R.string.attachment_video_label
                } else {
                    R.string.attachment_file_label
                },
            ),
        onDownload = onDownload,
        modifier = modifier,
        readyContent = {
            IconButton(onClick = onOpen) {
                Icon(
                    imageVector = ChattlyxIcons.Play,
                    contentDescription = stringResource(R.string.attachment_open),
                )
            }
        },
    )
}

@Composable
private fun TransferRow(
    attachment: AttachmentInfo,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    readyContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .widthIn(min = 200.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (attachment.state) {
                    AttachmentState.DOWNLOADING -> stringResource(R.string.attachment_downloading)
                    AttachmentState.FAILED -> stringResource(R.string.attachment_download_failed)
                    else -> formatSize(attachment.sizeBytes)
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
        when (attachment.state) {
            AttachmentState.READY -> readyContent?.invoke()
            AttachmentState.DOWNLOADING -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else -> IconButton(onClick = onDownload) {
                Icon(
                    imageVector = ChattlyxIcons.Download,
                    contentDescription = stringResource(R.string.attachment_download),
                )
            }
        }
    }
}

private fun AttachmentInfo.ratioOrNull(): Float? {
    val w = width?.toFloat()
    val h = height?.toFloat()
    if (w == null || h == null || w <= 0f || h <= 0f) return null
    return (w / h).coerceIn(0.5f, 2f)
}

internal fun formatDuration(millis: Int): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(Locale.ROOT, bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(Locale.ROOT, bytes / 1024.0)
    else -> "%d B".format(Locale.ROOT, bytes)
}

// Previews (master spec: light/dark/large-font/RTL) --------------------------

private fun previewMessage(state: AttachmentState) = com.chattlyx.domain.messaging.Message(
    id = "m1",
    clientId = "m1",
    serverId = "s1",
    conversationId = "dm:a:b",
    senderAccountId = "peer",
    body = "",
    status = com.chattlyx.domain.messaging.DeliveryStatus.DELIVERED,
    seq = 1,
    sentAt = 0,
    receivedAt = 0,
    isMine = false,
    attachment = AttachmentInfo(
        kind = AttachmentKind.VOICE,
        attachmentId = "att-1",
        mimeType = "audio/mp4",
        sizeBytes = 48_000,
        sha256 = ByteArray(32),
        width = null,
        height = null,
        durationMs = 95_000,
        fileName = "voice.m4a",
        key = null,
        nonce = null,
        state = state,
        localPath = null,
    ),
)

@androidx.compose.ui.tooling.preview.Preview(name = "Light", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@androidx.compose.ui.tooling.preview.Preview(name = "Large font", showBackground = true, fontScale = 1.6f)
@androidx.compose.ui.tooling.preview.Preview(name = "RTL", showBackground = true, locale = "ar")
@Composable
private fun AttachmentBubblePreview() {
    com.chattlyx.core.designsystem.theme.ChattlyxTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachmentBubble(
                message = previewMessage(AttachmentState.PENDING_DOWNLOAD),
                localFile = null,
                onDownload = {},
                onOpenImage = {},
                onOpenFile = {},
            )
            AttachmentBubble(
                message = previewMessage(AttachmentState.DOWNLOADING),
                localFile = null,
                onDownload = {},
                onOpenImage = {},
                onOpenFile = {},
            )
        }
    }
}

