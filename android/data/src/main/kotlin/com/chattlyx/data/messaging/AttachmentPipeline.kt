package com.chattlyx.data.messaging

import android.content.Context
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.fold
import com.chattlyx.core.common.result.getOrElse
import com.chattlyx.core.crypto.AttachmentCipher
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.DeclareAttachmentDto
import com.chattlyx.core.network.rest.safeCall
import com.chattlyx.domain.messaging.AttachmentKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okio.buffer
import timber.log.Timber

/** Result of MED-02: everything needed to embed the blob in an E2EE payload. */
data class UploadedAttachment(
    val attachmentId: String,
    val key: ByteArray,
    val nonce: ByteArray,
    /** SHA-256 over the ciphertext (uploads are content-addressed). */
    val ciphertextSha256: ByteArray,
    val plaintextSizeBytes: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UploadedAttachment) return false
        return attachmentId == other.attachmentId && key.contentEquals(other.key) &&
            nonce.contentEquals(other.nonce) &&
            ciphertextSha256.contentEquals(other.ciphertextSha256) &&
            plaintextSizeBytes == other.plaintextSizeBytes
    }

    override fun hashCode(): Int = attachmentId.hashCode()
}

/**
 * MED-01..04 transfer engine: encrypts plaintext to a temp ciphertext file,
 * declares + uploads it (server only ever sees ciphertext), and the reverse
 * for downloads (stream -> verify SHA-256 -> AES-GCM-chunked decrypt).
 */
@Singleton
class AttachmentPipeline @Inject constructor(
    private val api: ChattlyxServiceApi,
    @ApplicationContext private val context: Context,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val octetStream: MediaType = "application/octet-stream".toMediaTypeOrNull()
        ?: error("application/octet-stream must be parseable")

    /**
     * MED-02 upload path: encrypt -> declare -> PUT ciphertext.
     * [onProgress] reports (doneBytes, totalBytes) across both passes.
     */
    suspend fun upload(
        plaintext: File,
        kind: AttachmentKind,
        mimeType: String,
        recipientAccountId: String?,
        conversationId: String?,
        width: Int?,
        height: Int?,
        durationMs: Int?,
        fileName: String?,
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<UploadedAttachment> = withContext(ioDispatcher) {
        if (!plaintext.isFile) {
            return@withContext Result.failure(
                ChattlyError.Validation("file", "error_attachment_missing"),
            )
        }
        val plaintextSize = plaintext.length()
        val ciphertextTmp = File(context.cacheDir, "att-out-${java.util.UUID.randomUUID()}.bin")

        try {
            // Pass 1: AES-256-GCM chunked encryption; digest rides along.
            onProgress(0, plaintextSize * 2)
            val encryption = FileInputStream(plaintext).use { input ->
                FileOutputStream(ciphertextTmp).use { output ->
                    AttachmentCipher.encrypt(input, output)
                }
            }
            val ciphertextSize = ciphertextTmp.length()
            onProgress(ciphertextSize, ciphertextSize * 2)

            // Pass 2: declare, then PUT the ciphertext bytes.
            val declared = safeCall {
                api.declareAttachment(
                    DeclareAttachmentDto(
                        kind = kind.toWireName(),
                        mimeType = mimeType,
                        sizeBytes = ciphertextSize,
                        sha256 = encryption.digest.toHex(),
                        recipientAccountId = recipientAccountId,
                        conversationId = conversationId,
                        width = width,
                        height = height,
                        durationMs = durationMs,
                        fileName = fileName,
                    ),
                )
            }.getOrElse { return@withContext Result.failure(it) }

            val body = object : okhttp3.RequestBody() {
                private val delegate = ciphertextTmp.asRequestBody(octetStream)
                override fun contentType(): MediaType? = delegate.contentType()
                override fun contentLength(): Long = delegate.contentLength()
                override fun writeTo(sink: okio.BufferedSink) {
                    val counting = object : okio.ForwardingSink(sink) {
                        var written = 0L
                        override fun write(source: okio.Buffer, byteCount: Long) {
                            super.write(source, byteCount)
                            written += byteCount
                            onProgress(ciphertextSize + written, ciphertextSize * 2)
                        }
                    }.buffer()
                    delegate.writeTo(counting)
                    counting.flush()
                }
            }
            safeCall { api.uploadAttachmentData(declared.attachmentId, body) }
                .getOrElse { return@withContext Result.failure(it) }

            Result.success(
                UploadedAttachment(
                    attachmentId = declared.attachmentId,
                    key = encryption.key,
                    nonce = encryption.nonce,
                    ciphertextSha256 = encryption.digest,
                    plaintextSizeBytes = plaintextSize,
                ),
            )
        } catch (e: java.io.IOException) {
            Timber.w(e, "Attachment upload I/O failure (paths not logged)")
            Result.failure(ChattlyError.Storage.Io(e))
        } finally {
            if (!ciphertextTmp.delete()) ciphertextTmp.deleteOnExit()
        }
    }

    /**
     * MED-04 download path: GET meta -> GET ciphertext (streamed) -> verify
     * SHA-256 -> decrypt into filesDir/attachments/att-{id} (decrypted bytes
     * stay inside app-private storage).
     */
    suspend fun download(
        attachmentId: String,
        key: ByteArray,
        nonce: ByteArray,
        expectedSha256: ByteArray,
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(ioDispatcher) {
        val meta = safeCall { api.attachmentMeta(attachmentId) }
            .getOrElse { return@withContext Result.failure(it) }
        if (meta.status != "uploaded") {
            return@withContext Result.failure(ChattlyError.Server("attachments/not_uploaded", 404))
        }

        val tmp = File(context.cacheDir, "att-in-${java.util.UUID.randomUUID()}.bin")
        try {
            val response = api.downloadAttachmentData(attachmentId)
            if (!response.isSuccessful) return@withContext Result.failure(ChattlyError.Network())
            val body = response.body() ?: return@withContext Result.failure(ChattlyError.Network())

            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        onProgress(received, meta.sizeBytes.coerceAtLeast(received))
                    }
                }
            }
            if (!digest.digest().contentEquals(expectedSha256)) {
                Timber.w("Attachment digest mismatch; refusing to decrypt")
                return@withContext Result.failure(ChattlyError.Crypto.DecryptFailed)
            }

            val outputDir = File(context.filesDir, "attachments").apply { mkdirs() }
            val plaintext = File(outputDir, "att-$attachmentId")
            FileInputStream(tmp).use { input ->
                FileOutputStream(plaintext).use { output ->
                    AttachmentCipher.decrypt(input, output, key, nonce)
                }
            }
            Result.success(plaintext)
        } catch (e: java.io.IOException) {
            Timber.w(e, "Attachment download I/O failure (paths not logged)")
            Result.failure(ChattlyError.Storage.Io(e))
        } catch (e: Exception) {
            Timber.w(e, "Attachment decrypt failed")
            Result.failure(ChattlyError.Crypto.DecryptFailed)
        } finally {
            if (!tmp.delete()) tmp.deleteOnExit()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(Locale.ROOT, it) }
}
