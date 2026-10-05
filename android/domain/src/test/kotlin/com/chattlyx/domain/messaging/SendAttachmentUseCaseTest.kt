package com.chattlyx.domain.messaging

import androidx.paging.PagingData
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.usecases.DownloadAttachmentUseCase
import com.chattlyx.domain.messaging.usecases.SendAttachmentUseCase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/** MED-01/02/03 validation + delegation for outgoing attachments. */
class SendAttachmentUseCaseTest {

    private class RecordingMessageRepository : MessageRepository {
        var lastFile: File? = null
        var lastKind: AttachmentKind? = null
        var lastCaption: String? = null

        override suspend fun sendMessage(peerAccountId: String, body: String): Result<String> =
            Result.success("client-1")

        override suspend fun sendGroupMessage(groupId: String, body: String): Result<String> =
            Result.success("client-group-1")

        override suspend fun acknowledge(serverIds: List<String>) = Unit
        override suspend fun markRead(conversationId: String) = Unit
        override suspend fun sync(): Result<Unit> = Result.success(Unit)
        override suspend fun sendTyping(conversationId: String, started: Boolean) = Unit

        override suspend fun sendAttachment(
            peerAccountId: String,
            plaintextFile: File,
            kind: AttachmentKind,
            mimeType: String,
            fileName: String?,
            width: Int?,
            height: Int?,
            durationMs: Int?,
            caption: String,
        ): Result<String> {
            lastFile = plaintextFile
            lastKind = kind
            lastCaption = caption
            return Result.success("client-attachment-1")
        }

        override suspend fun downloadAttachment(message: Message): Result<File> =
            Result.failure(ChattlyError.Network())
    }

    private class NoopConversationRepository : ConversationRepository {
        override fun observeConversations(): Flow<PagingData<Conversation>> = flowOf(PagingData.empty())
        override fun observeConversation(conversationId: String): Flow<Conversation?> = flowOf(null)
        override fun observeMessages(conversationId: String): Flow<PagingData<Message>> = flowOf(PagingData.empty())
        override suspend fun openConversationWith(peerAccountId: String): String = "dm:x"
        override suspend fun setPinned(conversationId: String, pinned: Boolean) = Unit
    }

    private val messages = RecordingMessageRepository()
    private val useCase = SendAttachmentUseCase(messages, NoopConversationRepository())

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("att", ".bin").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    @Test
    fun `valid image forwards file kind and trimmed caption`() = runTest {
        val file = tempFile(byteArrayOf(1, 2, 3, 4))
        val result = useCase(
            peerAccountId = "peer-1",
            file = file,
            kind = AttachmentKind.IMAGE,
            mimeType = "image/jpeg",
            fileName = "pic.jpg",
            caption = "  look at this  ",
        )
        assertIs<Result.Success<String>>(result)
        assertEquals(file, messages.lastFile)
        assertEquals(AttachmentKind.IMAGE, messages.lastKind)
        assertEquals("look at this", messages.lastCaption)
    }

    @Test
    fun `missing file fails validation without sending`() = runTest {
        val result = useCase(
            peerAccountId = "peer-1",
            file = File("/definitely/not/here.bin"),
            kind = AttachmentKind.FILE,
            mimeType = "application/pdf",
        )
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.Validation>(failure.error)
        assertNull(messages.lastFile)
    }

    @Test
    fun `empty file fails validation`() = runTest {
        val file = tempFile(ByteArray(0))
        val result = useCase("peer-1", file, AttachmentKind.FILE, "application/pdf")
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.Validation>(failure.error)
    }

    @Test
    fun `oversized file fails validation`() = runTest {
        val file = File.createTempFile("att-big", ".bin").apply {
            deleteOnExit()
            // Sparse file: length without allocating.
            java.io.RandomAccessFile(this, "rw").setLength(SendAttachmentUseCase.MAX_ATTACHMENT_BYTES + 1)
        }
        val result = useCase("peer-1", file, AttachmentKind.VIDEO, "video/mp4")
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.Validation>(failure.error)
        assertNull(messages.lastFile)
    }

    @Test
    fun `blank mime type fails validation`() = runTest {
        val file = tempFile(byteArrayOf(9))
        val result = useCase("peer-1", file, AttachmentKind.FILE, "   ")
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.Validation>(failure.error)
    }

    @Test
    fun `download use case delegates to repository`() = runTest {
        val download = DownloadAttachmentUseCase(object : MessageRepository {
            override suspend fun sendMessage(peerAccountId: String, body: String) = Result.success("x")
            override suspend fun sendGroupMessage(groupId: String, body: String) = Result.success("g")
            override suspend fun acknowledge(serverIds: List<String>) = Unit
            override suspend fun markRead(conversationId: String) = Unit
            override suspend fun sync() = Result.success(Unit)
            override suspend fun sendTyping(conversationId: String, started: Boolean) = Unit
            override suspend fun sendAttachment(
                peerAccountId: String,
                plaintextFile: File,
                kind: AttachmentKind,
                mimeType: String,
                fileName: String?,
                width: Int?,
                height: Int?,
                durationMs: Int?,
                caption: String,
            ) = Result.success("y")

            override suspend fun downloadAttachment(message: Message): Result<File> =
                Result.success(File("ok.bin"))
        })
        val message = Message(
            id = "m1",
            clientId = "m1",
            serverId = "s1",
            conversationId = "dm:a:b",
            senderAccountId = "peer-1",
            body = "",
            status = DeliveryStatus.DELIVERED,
            seq = 1,
            sentAt = 0,
            receivedAt = 0,
            isMine = false,
        )
        val result = download(message)
        assertIs<Result.Success<File>>(result)
    }
}
