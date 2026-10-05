package com.chattlyx.feature.chats

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.ChattlyxTextField
import com.chattlyx.core.designsystem.component.DeliveryTickState
import com.chattlyx.core.designsystem.component.DeliveryTicks
import com.chattlyx.core.designsystem.component.MessageBubble
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import com.chattlyx.domain.messaging.DeliveryStatus
import com.chattlyx.domain.messaging.Message
import java.io.File
import java.text.DateFormat
import java.util.Date

/** MSG-03 + MED-01..04: history, composer, attachments, receipts, typing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val peerTyping by viewModel.peerTyping.collectAsStateWithLifecycle()
    val composer by viewModel.composerText.collectAsStateWithLifecycle()
    val attachmentSending by viewModel.attachmentSending.collectAsStateWithLifecycle()
    val recordingStartedAt by viewModel.recordingStartedAt.collectAsStateWithLifecycle()
    val snackKey by viewModel.snackMessageKey.collectAsStateWithLifecycle()
    val pagingItems = viewModel.messages.collectAsLazyPagingItems()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var viewerMessage by remember { mutableStateOf<Message?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onVisualMediaPicked(uri, preferVideo = false) }
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onVisualMediaPicked(uri, preferVideo = true) }
    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onDocumentPicked(uri) }
    val requestAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startRecording()
    }

    LaunchedEffect(snackKey) {
        val key = snackKey ?: return@LaunchedEffect
        val text = when (key) {
            "error_attachment_send" -> context.getString(R.string.error_attachment_send)
            else -> context.getString(R.string.error_attachment_download)
        }
        snackbarHostState.showSnackbar(text)
        viewModel.consumeSnack()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.markRead() }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = conversation?.peerName
                                ?: stringResource(R.string.conversation_loading),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (peerTyping) {
                            Text(
                                text = stringResource(R.string.conversation_typing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = ChattlyxIcons.Settings,
                            contentDescription = stringResource(R.string.conversation_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            val refreshing = pagingItems.loadState.refresh is LoadState.Loading

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (refreshing && pagingItems.itemCount == 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                    )
                } else {
                    // Newest first keeps keyboard-adjacent scrolling cheap;
                    // the list is read top-down by reversing visually.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(pagingItems.itemCount) { index ->
                            val message = pagingItems[index] ?: return@items
                            MessageRow(
                                message = message,
                                localFile = viewModel.localFile(message),
                                onDownload = viewModel::download,
                                onOpenImage = { viewerMessage = it },
                                onOpenFile = { openExternally(context, viewModel.localFile(it), it.attachment?.mimeType) },
                            )
                        }
                    }
                }
            }

            when {
                recordingStartedAt != null -> VoiceRecordingBar(
                    startedAt = recordingStartedAt ?: System.currentTimeMillis(),
                    onCancel = viewModel::cancelRecording,
                    onSend = viewModel::stopAndSendRecording,
                )
                attachmentSending -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.attachment_uploading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!viewModel.isGroup) {
                        IconButton(onClick = { attachMenuOpen = true }) {
                            Icon(
                                imageVector = ChattlyxIcons.Paperclip,
                                contentDescription = stringResource(R.string.attachment_add),
                            )
                        }
                    }
                    ChattlyxTextField(
                        value = composer,
                        onValueChange = viewModel::onTextChanged,
                        placeholder = stringResource(R.string.conversation_composer_hint),
                        singleLine = false,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(4.dp))
                    if (composer.isBlank() && !viewModel.isGroup) {
                        IconButton(onClick = {
                            if (android.content.pm.PackageManager.PERMISSION_GRANTED ==
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO,
                                )
                            ) {
                                viewModel.startRecording()
                            } else {
                                requestAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(
                                imageVector = ChattlyxIcons.Mic,
                                contentDescription = stringResource(R.string.attachment_voice),
                            )
                        }
                    } else {
                        ChattlyxButton(
                            text = stringResource(R.string.conversation_send),
                            onClick = viewModel::send,
                            variant = ChattlyxButtonVariant.FILLED,
                            enabled = composer.isNotBlank(),
                            modifier = Modifier.height(48.dp),
                        )
                    }
                }
            }
        }
    }

    if (attachMenuOpen) {
        AlertDialog(
            onDismissRequest = { attachMenuOpen = false },
            confirmButton = {},
            title = { Text(stringResource(R.string.attachment_add)) },
            text = {
                Column {
                    AttachOption(
                        icon = ChattlyxIcons.Image,
                        label = stringResource(R.string.attachment_photo),
                    ) {
                        attachMenuOpen = false
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    AttachOption(
                        icon = ChattlyxIcons.Play,
                        label = stringResource(R.string.attachment_video),
                    ) {
                        attachMenuOpen = false
                        pickVideo.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    }
                    AttachOption(
                        icon = ChattlyxIcons.FileDoc,
                        label = stringResource(R.string.attachment_file),
                    ) {
                        attachMenuOpen = false
                        pickDocument.launch(arrayOf("*/*"))
                    }
                    AttachOption(
                        icon = ChattlyxIcons.Mic,
                        label = stringResource(R.string.attachment_voice),
                    ) {
                        attachMenuOpen = false
                        if (android.content.pm.PackageManager.PERMISSION_GRANTED ==
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO,
                            )
                        ) {
                            viewModel.startRecording()
                        } else {
                            requestAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            },
        )
    }

    viewerMessage?.let { message ->
        val file = viewModel.localFile(message)
        if (file != null) {
            FullscreenImageDialog(file = file, onDismiss = { viewerMessage = null })
        }
    }
}

@Composable
private fun AttachOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun VoiceRecordingBar(
    startedAt: Long,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }
    val elapsed = ((now - startedAt) / 1000).coerceAtLeast(0)
    val timer = "%d:%02d".format(java.util.Locale.ROOT, elapsed / 60, elapsed % 60)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color.Red, androidx.compose.foundation.shape.CircleShape),
        )
        Text(
            text = stringResource(R.string.voice_recording, timer),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.voice_cancel))
        }
        IconButton(onClick = onSend) {
            Icon(
                imageVector = ChattlyxIcons.Mic,
                contentDescription = stringResource(R.string.voice_send),
            )
        }
    }
}

@Composable
private fun FullscreenImageDialog(file: File, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.attachment_image_label),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** MED-03: hands a decrypted blob to an external viewer via FileProvider. */
private fun openExternally(context: android.content.Context, file: File?, mimeType: String?) {
    if (file == null || !file.isFile) return
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        timber.log.Timber.w(e, "No handler for attachment open")
        android.widget.Toast
            .makeText(context, R.string.attachment_no_app, android.widget.Toast.LENGTH_SHORT)
            .show()
    }
}

@Composable
private fun MessageRow(
    message: Message,
    localFile: File?,
    onDownload: (Message) -> Unit,
    onOpenImage: (Message) -> Unit,
    onOpenFile: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    MessageBubble(
        direction = if (message.isMine) {
            com.chattlyx.core.designsystem.component.BubbleDirection.OUTGOING
        } else {
            com.chattlyx.core.designsystem.component.BubbleDirection.INCOMING
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            if (message.attachment != null) {
                AttachmentBubble(
                    message = message,
                    localFile = localFile,
                    onDownload = onDownload,
                    onOpenImage = onOpenImage,
                    onOpenFile = onOpenFile,
                )
            }
            if (message.body.isNotBlank()) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatTime(message.receivedAt ?: message.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (message.isMine) {
                    DeliveryTicks(state = message.status.toTickState())
                }
            }
        }
    }
}

private fun DeliveryStatus.toTickState(): DeliveryTickState = when (this) {
    DeliveryStatus.PENDING -> DeliveryTickState.PENDING
    DeliveryStatus.SENT -> DeliveryTickState.SENT
    DeliveryStatus.DELIVERED -> DeliveryTickState.DELIVERED
    DeliveryStatus.READ -> DeliveryTickState.READ
    DeliveryStatus.FAILED -> DeliveryTickState.FAILED
}

private fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
