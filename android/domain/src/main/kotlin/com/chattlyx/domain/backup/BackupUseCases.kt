package com.chattlyx.domain.backup

import com.chattlyx.core.common.result.Result
import javax.inject.Inject

/** BKP-01: export the local store to a passphrase-encrypted blob. */
class ExportBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(passphrase: String): Result<ByteArray> =
        backupRepository.exportBackup(passphrase)
}

/** BKP-02: restore from a blob produced by [ExportBackupUseCase]. */
class RestoreBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(
        blob: ByteArray,
        passphrase: String,
    ): Result<RestoreSummary> = backupRepository.restoreBackup(blob, passphrase)
}
