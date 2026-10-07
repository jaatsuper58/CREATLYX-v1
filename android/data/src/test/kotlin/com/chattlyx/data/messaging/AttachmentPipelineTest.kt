package com.chattlyx.data.messaging

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.crypto.AttachmentCipher
import com.chattlyx.core.network.rest.AttachmentMetaDto
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.DeclareAttachmentDto
import com.chattlyx.core.network.rest.DeclaredAttachmentDto
import com.chattlyx.domain.messaging.AttachmentKind
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

/**
 * MED-01..04 transfer engine: ciphertext-only uploads, content-addressed
 * declares, digest-verified downloads, AES-GCM roundtrip.
 */
@RunWith(RobolectricTestRunner::class)
class AttachmentPipelineTest {

    private lateinit var api: ChattlyxServiceApi
    private lateinit var pipeline: AttachmentPipeline

    @Before
    fun setUp() {
        api = mockk()
        pipeline = AttachmentPipeline(
            api = api,
            context = RuntimeEnvironment.getApplication(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun plaintextFile(bytes: ByteArray): File =
        File.createTempFile("plain", ".bin").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    @Test
    fun `upload declares ciphertext digest and uploads non-plaintext bytes`() = runTest {
        val plaintext = "chattlyx phase three payload".repeat(64).toByteArray()
        val file = plaintextFile(plaintext)

        val declareBody = slot<DeclareAttachmentDto>()
        coEvery { api.declareAttachment(capture(declareBody)) } returns
            Response.success(DeclaredAttachmentDto("att-1", "/v1/attachments/att-1/data"))

        // Capture the uploaded bytes DURING the call: the pipeline deletes
        // its temp ciphertext file as soon as upload() returns.
        var uploadedDuringCall = ByteArray(0)
        coEvery { api.uploadAttachmentData("att-1", any()) } answers {
            val body = secondArg<RequestBody>()
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            uploadedDuringCall = buffer.readByteArray()
            Response.success(Unit)
        }

        val result = pipeline.upload(
            plaintext = file,
            kind = AttachmentKind.IMAGE,
            mimeType = "image/jpeg",
            recipientAccountId = "peer-1",
            conversationId = "dm:a:b",
            width = 640,
            height = 480,
            durationMs = null,
            fileName = "pic.jpg",
        )
        val uploaded = (result as Result.Success).value

        assertEquals("att-1", uploaded.attachmentId)
        assertEquals(AttachmentCipher.KEY_BYTES, uploaded.key.size)
        assertEquals(AttachmentCipher.NONCE_BYTES, uploaded.nonce.size)
        assertEquals(plaintext.size.toLong(), uploaded.plaintextSizeBytes)
        assertEquals("image", declareBody.captured.kind)
        assertEquals("peer-1", declareBody.captured.recipientAccountId)
        assertEquals(640, declareBody.captured.width)

        // The uploaded bytes are ciphertext: digest matches the declaration,
        // size is plaintext + GCM overhead, and they differ from plaintext.
        val uploadedBytes = uploadedDuringCall
        assertEquals(declareBody.captured.sizeBytes, uploadedBytes.size.toLong())
        assertTrue(uploadedBytes.size > plaintext.size)
        val declaredSha = declareBody.captured.sha256
        val actualSha = java.security.MessageDigest.getInstance("SHA-256").digest(uploadedBytes)
            .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
        assertEquals(declaredSha, actualSha)
        assertTrue(uploaded.ciphertextSha256.contentEquals(actualSha.let {
            ByteArray(it.length / 2) { i -> it.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }))
        assertTrue(!uploadedBytes.contentEquals(plaintext))
    }

    @Test
    fun `upload reports progress across both passes`() = runTest {
        val file = plaintextFile(ByteArray(4096) { it.toByte() })
        coEvery { api.declareAttachment(any()) } returns
            Response.success(DeclaredAttachmentDto("att-2", "/v1/attachments/att-2/data"))
        coEvery { api.uploadAttachmentData("att-2", any()) } returns Response.success(Unit)

        var ticks = 0
        var maxDone = 0L
        pipeline.upload(
            plaintext = file,
            kind = AttachmentKind.FILE,
            mimeType = "application/pdf",
            recipientAccountId = null,
            conversationId = null,
            width = null,
            height = null,
            durationMs = null,
            fileName = null,
            onProgress = { done, _ -> ticks++; maxDone = maxOf(maxDone, done) },
        )
        assertTrue(ticks >= 2)
        assertTrue(maxDone > 0)
    }

    @Test
    fun `download verifies digest and decrypts to app-private storage`() = runTest {
        val original = "voice note bytes".repeat(256).toByteArray()
        val key = AttachmentCipher.generateKey()
        val nonce = AttachmentCipher.generateNonce()
        val out = ByteArrayOutputStream()
        java.io.ByteArrayInputStream(original).use { input ->
            AttachmentCipher.encrypt(input, out, key, nonce)
        }
        val ciphertext = out.toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(ciphertext)

        coEvery { api.attachmentMeta("att-9") } returns Response.success(
            AttachmentMetaDto(
                attachmentId = "att-9",
                kind = "voice",
                mimeType = "audio/mp4",
                sizeBytes = ciphertext.size.toLong(),
                sha256 = sha.joinToString("") { "%02x".format(java.util.Locale.ROOT, it) },
                status = "ready",
            ),
        )
        coEvery { api.downloadAttachmentData("att-9") } returns
            Response.success(ciphertext.toResponseBody("application/octet-stream".toMediaType()))

        val result = pipeline.download("att-9", key, nonce, sha)
        val file = (result as Result.Success).value
        assertTrue(file.isFile)
        assertTrue(file.absolutePath.contains("attachments"))
        assertTrue(file.readBytes().contentEquals(original))
    }

    @Test
    fun `download rejects tampered ciphertext via digest check`() = runTest {
        val original = "tamper me".toByteArray()
        val key = AttachmentCipher.generateKey()
        val nonce = AttachmentCipher.generateNonce()
        val ciphertext = ByteArrayOutputStream().use { out ->
            java.io.ByteArrayInputStream(original).use { input ->
                AttachmentCipher.encrypt(input, out, key, nonce)
            }
            out.toByteArray()
        }
        val expectedSha = java.security.MessageDigest.getInstance("SHA-256").digest(ciphertext)
        ciphertext[0] = (ciphertext[0].toInt() xor 0xFF).toByte()

        coEvery { api.attachmentMeta("att-8") } returns Response.success(
            AttachmentMetaDto(
                attachmentId = "att-8",
                kind = "file",
                mimeType = "application/pdf",
                sizeBytes = ciphertext.size.toLong(),
                sha256 = expectedSha.joinToString("") { "%02x".format(java.util.Locale.ROOT, it) },
                status = "ready",
            ),
        )
        coEvery { api.downloadAttachmentData("att-8") } returns
            Response.success(ciphertext.toResponseBody("application/octet-stream".toMediaType()))

        val result = pipeline.download("att-8", key, nonce, expectedSha)
        val failure = result as Result.Failure
        assertEquals(ChattlyError.Crypto.DecryptFailed, failure.error)
    }

    @Test
    fun `download fails while blob still pending`() = runTest {
        coEvery { api.attachmentMeta("att-7") } returns Response.success(
            AttachmentMetaDto(
                attachmentId = "att-7",
                kind = "image",
                mimeType = "image/jpeg",
                sizeBytes = 10,
                sha256 = "00",
                status = "pending",
            ),
        )
        val result = pipeline.download("att-7", ByteArray(32), ByteArray(12), ByteArray(32))
        val failure = result as Result.Failure
        assertTrue(failure.error is ChattlyError.Server)
    }

    @Test
    fun `upload of missing file fails with validation error`() = runTest {
        val result = pipeline.upload(
            plaintext = File("/definitely/not/here.bin"),
            kind = AttachmentKind.FILE,
            mimeType = "application/pdf",
            recipientAccountId = null,
            conversationId = null,
            width = null,
            height = null,
            durationMs = null,
            fileName = null,
        )
        val failure = result as Result.Failure
        assertTrue(failure.error is ChattlyError.Validation)
    }
}
