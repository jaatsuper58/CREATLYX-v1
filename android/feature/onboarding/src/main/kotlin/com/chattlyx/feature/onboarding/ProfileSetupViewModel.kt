package com.chattlyx.feature.onboarding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.usecases.UpdateProfileUseCase
import com.chattlyx.domain.auth.usecases.UploadAvatarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Profile-setup state (AUTH-04 completion step). */
data class ProfileSetupState(
    val displayName: String = "",
    val username: String = "",
    val about: String = "",
    val avatarJpeg: ByteArray? = null,
    val saving: Boolean = false,
    val errorRes: Int? = null,
    val done: Boolean = false,
) {
    val canSave: Boolean
        get() = displayName.trim().isNotEmpty() && !saving
}

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val uploadAvatarUseCase: UploadAvatarUseCase,
    @Dispatcher(ChattlyxDispatcher.DEFAULT) private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSetupState())
    val state: StateFlow<ProfileSetupState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update {
        it.copy(displayName = value.take(MAX_NAME), errorRes = null)
    }

    fun onUsernameChange(value: String) = _state.update {
        it.copy(username = value.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }, errorRes = null)
    }

    fun onAboutChange(value: String) = _state.update {
        it.copy(about = value.take(MAX_ABOUT), errorRes = null)
    }

    /** Downscale-picks the chosen photo into a JPEG avatar (~256px). */
    fun onAvatarPicked(rawImageBytes: ByteArray) {
        viewModelScope.launch(dispatcher) {
            val scaled = downscaleToJpeg(rawImageBytes, AVATAR_SIZE_PX)
            if (scaled != null) {
                _state.update { it.copy(avatarJpeg = scaled) }
            }
        }
    }

    /**
     * Saves the profile: uploads the encrypted avatar first (when present),
     * then writes the profile with the returned blob id.
     */
    fun save() {
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(saving = true, errorRes = null) }

            var avatarBlobId: String? = null
            val avatar = current.avatarJpeg
            if (avatar != null) {
                when (val upload = uploadAvatarUseCase(avatar)) {
                    is Result.Success -> avatarBlobId = upload.value
                    is Result.Failure -> {
                        _state.update {
                            it.copy(saving = false, errorRes = upload.error.toMessageRes())
                        }
                        return@launch
                    }
                }
            }

            when (
                val result = updateProfileUseCase(
                    displayName = current.displayName,
                    username = current.username.ifBlank { null },
                    about = current.about,
                    avatarBlobId = avatarBlobId,
                )
            ) {
                is Result.Success -> _state.update { it.copy(saving = false, done = true) }
                is Result.Failure -> {
                    val errorRes = result.error.toMessageRes()
                    _state.update { it.copy(saving = false, errorRes = errorRes) }
                }
            }
        }
    }

    private fun downscaleToJpeg(bytes: ByteArray, maxEdge: Int): ByteArray? = try {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scale = maxOf(1, maxOf(source.width, source.height) / maxEdge)
        val scaled = if (scale == 1) {
            source
        } else {
            Bitmap.createScaledBitmap(source, source.width / scale, source.height / scale, true)
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        out.toByteArray()
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun ChattlyError.toMessageRes(): Int = when (this) {
        is ChattlyError.Validation -> R.string.onboarding_profile_invalid
        is ChattlyError.Network -> R.string.onboarding_error_network
        is ChattlyError.Server -> when (code) {
            "profile/username-taken" -> R.string.onboarding_username_taken
            else -> R.string.onboarding_error_server
        }

        else -> R.string.onboarding_error_server
    }

    private companion object {
        const val MAX_NAME = 40
        const val MAX_ABOUT = 140
        const val AVATAR_SIZE_PX = 256
    }
}
