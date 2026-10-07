package com.chattlyx.feature.settings.storage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.domain.backup.BackupRepository
import com.chattlyx.domain.backup.ExportBackupUseCase
import com.chattlyx.domain.backup.RestoreBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Result banner for the storage screen; keys map to strings resources. */
sealed interface StorageMessage {
    data object Exported : StorageMessage
    data class Restored(val conversations: Int, val messages: Int) : StorageMessage
    data object PassphraseTooShort : StorageMessage
    data object WrongPassphrase : StorageMessage
    data object CorruptFile : StorageMessage
    data object IoFailure : StorageMessage
}

data class StorageUiState(
    val backupPassphrase: String = "",
    val restorePassphrase: String = "",
    val busy: Boolean = false,
    val message: StorageMessage? = null,
)

/** BKP-01/02: drives SAF-based encrypted backup export and restore. */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val exportBackup: ExportBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    @ApplicationContext private val context: Context,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    fun onBackupPassphraseChange(value: String) {
        _state.update { it.copy(backupPassphrase = value, message = null) }
    }

    fun onRestorePassphraseChange(value: String) {
        _state.update { it.copy(restorePassphrase = value, message = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /** BKP-01: encrypt the local store and stream it into the picked SAF file. */
    suspend fun exportTo(uri: Uri) {
        val passphrase = _state.value.backupPassphrase
        if (passphrase.length < BackupRepository.MIN_PASSPHRASE_LENGTH) {
            _state.update { it.copy(message = StorageMessage.PassphraseTooShort) }
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        val outcome = runCatching {
            when (val result = exportBackup(passphrase)) {
                is Result.Success -> {
                    withContext(ioDispatcher) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(result.value) }
                            ?: throw IOException("unwritable destination")
                    }
                    StorageMessage.Exported
                }
                is Result.Failure -> mapFailure(result.error)
            }
        }.getOrElse { error ->
            Timber.w(error, "Backup export I/O failed")
            StorageMessage.IoFailure
        }
        _state.update { it.copy(busy = false, message = outcome) }
    }

    /** BKP-02: read the picked SAF file and merge it into the local store. */
    suspend fun restoreFrom(uri: Uri) {
        val passphrase = _state.value.restorePassphrase
        _state.update { it.copy(busy = true, message = null) }
        val outcome = runCatching {
            val blob = withContext(ioDispatcher) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("unreadable source")
            }
            when (val result = restoreBackup(blob, passphrase)) {
                is Result.Success ->
                    StorageMessage.Restored(
                        conversations = result.value.conversationsRestored,
                        messages = result.value.messagesRestored,
                    )
                is Result.Failure -> mapFailure(result.error)
            }
        }.getOrElse { error ->
            Timber.w(error, "Backup restore I/O failed")
            StorageMessage.IoFailure
        }
        _state.update { it.copy(busy = false, message = outcome) }
    }

    private fun mapFailure(error: ChattlyError): StorageMessage = when {
        error is ChattlyError.Validation && error.messageKey == "error_backup_passphrase_short" ->
            StorageMessage.PassphraseTooShort
        error is ChattlyError.Validation && error.messageKey == "error_backup_wrong_passphrase" ->
            StorageMessage.WrongPassphrase
        error is ChattlyError.Validation && error.messageKey == "error_backup_corrupt" ->
            StorageMessage.CorruptFile
        else -> StorageMessage.IoFailure
    }
}
