package com.chattlyx.data.backup

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.crypto.BackupCipher
import com.chattlyx.core.database.dao.ConversationDao
import com.chattlyx.core.database.dao.MessageDao
import com.chattlyx.core.database.entity.ConversationEntity
import com.chattlyx.core.database.entity.MessageEntity
import com.chattlyx.domain.backup.BackupRepository
import com.chattlyx.domain.backup.RestoreSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * BKP-01/02 implementation: Room snapshot -> JSON -> AES-256-GCM under a
 * PBKDF2-derived passphrase key. Everything stays on-device; the blob is
 * handed to the caller (SAF) for storage. Restore is idempotent: INSERT OR
 * IGNORE means existing rows always win over the backup copy.
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {

    override suspend fun exportBackup(passphrase: String): Result<ByteArray> =
        withContext(ioDispatcher) {
            if (passphrase.length < BackupRepository.MIN_PASSPHRASE_LENGTH) {
                return@withContext Result.failure(
                    ChattlyError.Validation("passphrase", "error_backup_passphrase_short"),
                )
            }
            try {
                val payload = JSONObject()
                    .put("format", FORMAT_NAME)
                    .put("version", FORMAT_VERSION)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("conversations", JSONArray(conversationDao.snapshotAll().map { it.toJson() }))
                    .put("messages", JSONArray(messageDao.snapshotAll().map { it.toJson() }))
                Result.success(BackupCipher.encrypt(payload.toString().toByteArray(Charsets.UTF_8), passphrase))
            } catch (e: Exception) {
                Timber.w(e, "Backup export failed")
                Result.failure(ChattlyError.Storage.Io(e))
            }
        }

    override suspend fun restoreBackup(blob: ByteArray, passphrase: String): Result<RestoreSummary> =
        withContext(ioDispatcher) {
            val plaintext = try {
                BackupCipher.decrypt(blob, passphrase)
            } catch (e: BackupCipher.WrongPassphraseException) {
                return@withContext Result.failure(
                    ChattlyError.Validation("passphrase", "error_backup_wrong_passphrase"),
                )
            } catch (e: Exception) {
                Timber.w(e, "Backup blob rejected")
                return@withContext Result.failure(
                    ChattlyError.Validation("file", "error_backup_corrupt"),
                )
            }

            try {
                val payload = JSONObject(plaintext.toString(Charsets.UTF_8))
                if (payload.optString("format") != FORMAT_NAME) {
                    return@withContext Result.failure(
                        ChattlyError.Validation("file", "error_backup_corrupt"),
                    )
                }
                var conversations = 0
                var messages = 0
                payload.optJSONArray("conversations")?.let { array ->
                    for (index in 0 until array.length()) {
                        val inserted = conversationDao.insertOrIgnore(array.getJSONObject(index).toConversation())
                        if (inserted != -1L) conversations += 1
                    }
                }
                payload.optJSONArray("messages")?.let { array ->
                    for (index in 0 until array.length()) {
                        val inserted = messageDao.insertOrIgnore(array.getJSONObject(index).toMessage())
                        if (inserted != -1L) messages += 1
                    }
                }
                Result.success(RestoreSummary(conversations, messages))
            } catch (e: Exception) {
                Timber.w(e, "Backup restore payload unparseable")
                Result.failure(ChattlyError.Validation("file", "error_backup_corrupt"))
            }
        }

    private companion object {
        const val FORMAT_NAME = "chattlyx-backup"
        const val FORMAT_VERSION = 1
    }
}

// --- JSON mappers (org.json keeps the module free of serialization plugins) ---

private fun ConversationEntity.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("peerAccountId", peerAccountId)
    .put("peerName", peerName)
    .put("peerAvatarBlobId", peerAvatarBlobId)
    .put("lastMessageText", lastMessageText)
    .put("lastMessageAt", lastMessageAt)
    .put("lastSeq", lastSeq)
    .put("unreadCount", unreadCount)
    .put("pinned", pinned)
    .put("archived", archived)
    .put("createdAt", createdAt)

private fun JSONObject.toConversation(): ConversationEntity = ConversationEntity(
    id = getString("id"),
    peerAccountId = getString("peerAccountId"),
    peerName = optString("peerName", ""),
    peerAvatarBlobId = optStringOrNull("peerAvatarBlobId"),
    lastMessageText = optString("lastMessageText", ""),
    lastMessageAt = optLong("lastMessageAt", 0L),
    lastSeq = optLong("lastSeq", 0L),
    unreadCount = optInt("unreadCount", 0),
    pinned = optBoolean("pinned", false),
    archived = optBoolean("archived", false),
    createdAt = optLong("createdAt", 0L),
)

private fun MessageEntity.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("clientId", clientId)
    .put("serverId", serverId)
    .put("conversationId", conversationId)
    .put("senderAccountId", senderAccountId)
    .put("body", body)
    .put("status", status)
    .put("seq", seq)
    .put("sentAt", sentAt)
    .put("receivedAt", receivedAt)
    .put("attachmentKind", attachmentKind)
    .put("attachmentId", attachmentId)
    .put("attachmentMime", attachmentMime)
    .put("attachmentSize", attachmentSize)
    .put("attachmentSha256", attachmentSha256)
    .put("attachmentWidth", attachmentWidth)
    .put("attachmentHeight", attachmentHeight)
    .put("attachmentDurationMs", attachmentDurationMs)
    .put("attachmentFileName", attachmentFileName)
    .put("attachmentKey", attachmentKey)
    .put("attachmentNonce", attachmentNonce)
    .put("attachmentState", attachmentState)
    .put("attachmentLocalPath", attachmentLocalPath)

private fun JSONObject.toMessage(): MessageEntity = MessageEntity(
    id = getString("id"),
    clientId = getString("clientId"),
    serverId = optStringOrNull("serverId"),
    conversationId = getString("conversationId"),
    senderAccountId = getString("senderAccountId"),
    body = optString("body", ""),
    status = optInt("status", 0),
    seq = optLong("seq", 0L),
    sentAt = getLong("sentAt"),
    receivedAt = if (isNull("receivedAt")) null else optLong("receivedAt"),
    attachmentKind = optStringOrNull("attachmentKind"),
    attachmentId = optStringOrNull("attachmentId"),
    attachmentMime = optStringOrNull("attachmentMime"),
    attachmentSize = if (isNull("attachmentSize")) null else optLong("attachmentSize"),
    attachmentSha256 = optStringOrNull("attachmentSha256"),
    attachmentWidth = if (isNull("attachmentWidth")) null else optInt("attachmentWidth"),
    attachmentHeight = if (isNull("attachmentHeight")) null else optInt("attachmentHeight"),
    attachmentDurationMs = if (isNull("attachmentDurationMs")) null else optInt("attachmentDurationMs"),
    attachmentFileName = optStringOrNull("attachmentFileName"),
    attachmentKey = optStringOrNull("attachmentKey"),
    attachmentNonce = optStringOrNull("attachmentNonce"),
    attachmentState = optStringOrNull("attachmentState"),
    attachmentLocalPath = null, // device-specific paths never round-trip
)

/** org.json returns the "null" literal for missing keys — normalise. */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }
