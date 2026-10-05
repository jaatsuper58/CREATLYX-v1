package com.chattlyx.feature.chats

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.AttachmentKind
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.Message
import com.chattlyx.domain.messaging.MessageRepository
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.usecases.DownloadAttachmentUseCase
import com.chattlyx.domain.messaging.usecases.MarkConversationReadUseCase
import com.chattlyx.domain.messaging.usecases.ObserveConversationUseCase
import com.chattlyx.domain.messaging.usecases.ObserveMessagesUseCase
import com.chattlyx.domain.messaging.usecases.SendAttachmentUseCase
import com.chattlyx.domain.messaging.usecases.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher

/** MSG-03 + MED-01..04: messages, composer, attachments, typing in/out. */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMessages: ObserveMessagesUseCase,
    observeConversation: ObserveConversationUseCase,
    realtimeEvents: RealtimeEvents,
    @ApplicationContext private val context: Context,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    private val messageRepository: MessageRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendAttachmentUseCase: SendAttachmentUseCase,
    private val downloadAttachmentUseCase: DownloadAttachmentUseCase,
    private val markConversationReadUseCase: MarkConversationReadUseCase,
) : ViewModel() {

    // Type-safe navigation stores route arguments under their declared name;
    // reading the key directly keeps the ViewModel unit-testable without the
    // Bundle-backed route decoder.
    private val conversationId: String = requireNotNull(savedStateHandle["conversationId"]) {
        "conversationId route argument is required"
    }

    val messages: Flow<PagingData<Message>> =
        observeMessages(conversationId).cachedIn(viewModelScope)

    /** Conversation header info (peer name), null until loaded. */
    val conversation: StateFlow<Conversation?> = observeConversation(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Peer typing indicator for this conversation. */
    val peerTyping: StateFlow<Boolean> = realtimeEvents.typingEvents
        .filter { it.conversationId == conversationId }
        .map { it.started }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val composerText = MutableStateFlow("")

    /** GRP-*: group conversations gate attachments (1:1 only in Phase 4). */
    val isGroup: Boolean = conversationId.startsWith("grp:")

    /** MED-02: true while an attachment is encrypting/uploading. */
    private val _attachmentSending = MutableStateFlow(false)
    val attachmentSending: StateFlow<Boolean> = _attachmentSending.asStateFlow()

    /** One-shot snackbar content (message-key string), consumed by the UI. */
    private val _snackMessageKey = MutableStateFlow<String?>(null)
    val snackMessageKey: StateFlow<String?> = _snackMessageKey.asStateFlow()

    /** MED-03: epoch millis while recording, null when idle. */
    private val _recordingStartedAt = MutableStateFlow<Long?>(null)
    val recordingStartedAt: StateFlow<Long?> = _recordingStartedAt.asStateFlow()

    private var typingStopJob: Job? = null
    private val voiceRecorder = VoiceRecorder(context)

    init {
        // Entering a conversation marks it read and receipts the peer.
        viewModelScope.launch { markConversationReadUseCase(conversationId) }
    }

    fun onTextChanged(value: String) {
        composerText.value = value
        emitTyping(started = true)
    }

    fun send() {
        val body = composerText.value.trim()
        val peer = conversation.value?.peerAccountId
        if (body.isEmpty() || peer == null) return

        composerText.value = ""
        if (isGroup) {
            // GRP-02: fan-out group text (peerAccountId carries the groupId).
            viewModelScope.launch { messageRepository.sendGroupMessage(peer, body) }
            return
        }
        emitTyping(started = false)
        viewModelScope.launch {
            // Outgoing row appears immediately via the store (PENDING status);
            // failures flip it to FAILED for retry affordances.
            sendMessageUseCase(peer, body)
        }
    }

    /** MED-01: image/video picked via the system photo picker. */
    fun onVisualMediaPicked(uri: Uri?, preferVideo: Boolean) {
        if (uri == null) return
        viewModelScope.launch {
            resolveAndSend(uri, preferVideo = preferVideo)
        }
    }

    /** MED-03: document picked via SAF. */
    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch { resolveAndSend(uri, preferVideo = false) }
    }

    /** MED-03: starts voice capture; requires RECORD_AUDIO already granted. */
    fun startRecording(): Boolean {
        val started = voiceRecorder.start()
        if (started) _recordingStartedAt.value = System.currentTimeMillis()
        return started
    }

    /** MED-03: stops capture and sends the note. */
    fun stopAndSendRecording() {
        _recordingStartedAt.value = null
        val recording = voiceRecorder.stop() ?: return
        viewModelScope.launch {
            val peer = conversation.value?.peerAccountId ?: return@launch
            sendAttachmentFile(
                peer = peer,
                file = recording.file,
                kind = AttachmentKind.VOICE,
                mimeType = "audio/mp4",
                fileName = null,
                width = null,
                height = null,
                durationMs = recording.durationMs.toInt().takeIf { it > 0 },
            )
        }
    }

    /** MED-03: discards the in-progress voice note. */
    fun cancelRecording() {
        _recordingStartedAt.value = null
        voiceRecorder.cancel()
    }

    /** MED-04: fetches + decrypts one attachment on demand. */
    fun download(message: Message) {
        viewModelScope.launch {
            downloadAttachmentUseCase(message).let { result ->
                if (result is Result.Failure) {
                    _snackMessageKey.value = "error_attachment_download"
                }
            }
        }
    }

    /** Local decrypted file for an attachment that is READY. */
    fun localFile(message: Message): File? =
        message.attachment?.localPath?.let(::File)?.takeIf(File::isFile)

    fun consumeSnack() {
        _snackMessageKey.value = null
    }

    fun markRead() {
        viewModelScope.launch { markConversationReadUseCase(conversationId) }
    }

    override fun onCleared() {
        voiceRecorder.cancel()
        super.onCleared()
    }

    // ------------------------------------------------------------------

    /** Copies a picked Uri to a staged file and extracts media metadata. */
    private suspend fun resolveAndSend(uri: Uri, preferVideo: Boolean) {
        val peer = conversation.value?.peerAccountId ?: return
        val resolved = withContext(ioDispatcher) {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val staged = File(context.cacheDir, "pick-${java.util.UUID.randomUUID()}")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    staged.outputStream().use { input.copyTo(it) }
                } ?: return@withContext null
            } catch (e: java.io.IOException) {
                Timber.w(e, "Failed to stage picked media")
                return@withContext null
            }
            val displayName = queryDisplayName(uri)
            val isVideo = mimeType.startsWith("video/")
            val isImage = mimeType.startsWith("image/")
            val kind = when {
                isVideo -> AttachmentKind.VIDEO
                isImage -> AttachmentKind.IMAGE
                else -> AttachmentKind.FILE
            }
            val meta = when (kind) {
                AttachmentKind.IMAGE -> imageDimensions(staged)
                AttachmentKind.VIDEO -> videoMetadata(staged)
                else -> null
            }
            PickedMedia(
                file = staged,
                kind = kind,
                mimeType = mimeType,
                fileName = displayName,
                width = meta?.width,
                height = meta?.height,
                durationMs = meta?.durationMs,
            )
        } ?: return

        sendAttachmentFile(
            peer = peer,
            file = resolved.file,
            kind = resolved.kind,
            mimeType = resolved.mimeType,
            fileName = resolved.fileName,
            width = resolved.width,
            height = resolved.height,
            durationMs = resolved.durationMs,
        )
    }

    private suspend fun sendAttachmentFile(
        peer: String,
        file: File,
        kind: AttachmentKind,
        mimeType: String,
        fileName: String?,
        width: Int?,
        height: Int?,
        durationMs: Int?,
    ) {
        _attachmentSending.value = true
        val result = sendAttachmentUseCase(
            peerAccountId = peer,
            file = file,
            kind = kind,
            mimeType = mimeType,
            fileName = fileName,
            width = width,
            height = height,
            durationMs = durationMs,
            caption = composerText.value.trim(),
        )
        _attachmentSending.value = false
        if (result is Result.Failure) {
            _snackMessageKey.value = "error_attachment_send"
        } else {
            composerText.value = ""
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (e: Exception) {
        null
    }

    private data class PickedMedia(
        val file: File,
        val kind: AttachmentKind,
        val mimeType: String,
        val fileName: String?,
        val width: Int?,
        val height: Int?,
        val durationMs: Int?,
    )

    private data class MediaMeta(val width: Int?, val height: Int?, val durationMs: Int?)

    private fun imageDimensions(file: File): MediaMeta {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            BitmapFactory.decodeFile(file.absolutePath, options)
            MediaMeta(options.outWidth, options.outHeight, null)
        } catch (e: Exception) {
            MediaMeta(null, null, null)
        }
    }

    private fun videoMetadata(file: File): MediaMeta {
        // MediaMetadataRetriever is only AutoCloseable on API 29+; release
        // manually to stay safe on minSdk 24.
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            MediaMeta(
                width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
                )?.toIntOrNull(),
                height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                )?.toIntOrNull(),
                durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toIntOrNull(),
            )
        } catch (e: Exception) {
            MediaMeta(null, null, null)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Already released; nothing to do.
            }
        }
    }

    /**
     * Sends typing started=true and schedules the stop signal after a pause,
     * so the peer's indicator never sticks.
     */
    private fun emitTyping(started: Boolean) {
        typingStopJob?.cancel()
        viewModelScope.launch { messageRepository.sendTyping(conversationId, started) }
        if (started) {
            typingStopJob = viewModelScope.launch {
                delay(TYPING_STOP_AFTER_MS)
                messageRepository.sendTyping(conversationId, false)
            }
        }
    }

    private companion object {
        const val TYPING_STOP_AFTER_MS = 3_000L
    }
}
