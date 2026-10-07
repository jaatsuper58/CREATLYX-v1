package com.chattlyx.domain.backup

import com.chattlyx.core.common.result.Result

/** Outcome of a restore (BKP-02): how many rows were newly added. */
data class RestoreSummary(
    val conversationsRestored: Int,
    val messagesRestored: Int,
)

/**
 * BKP-01/02 local, user-controlled chat backup. Backups never touch the
 * network: the payload is the local message store, encrypted with a key
 * derived from a passphrase the user chooses (see core:crypto JcaBackupCipher).
 */
interface BackupRepository {

    /**
     * Exports conversations + messages as an encrypted backup blob.
     * @param passphrase user-chosen secret; must meet [MIN_PASSPHRASE_LENGTH].
     */
    suspend fun exportBackup(passphrase: String): Result<ByteArray>

    /**
     * Restores a blob produced by [exportBackup]. Existing rows win (restore
     * is idempotent and never overwrites newer local state).
     */
    suspend fun restoreBackup(blob: ByteArray, passphrase: String): Result<RestoreSummary>

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}
