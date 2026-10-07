package com.chattlyx.domain.auth.usecases

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.AvatarImageSource
import com.chattlyx.domain.auth.Profile
import com.chattlyx.domain.auth.ProfileRepository
import javax.inject.Inject

/** AUTH-04: fetches the current profile. */
class GetProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): Result<Profile> = profileRepository.getProfile()
}

/** AUTH-04: updates display name / username / about with local validation. */
class UpdateProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {

    suspend operator fun invoke(
        displayName: String,
        username: String?,
        about: String,
        avatarBlobId: String? = null,
    ): Result<Profile> {
        val name = displayName.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) {
            return Result.failure(
                ChattlyError.Validation(field = "displayName", messageKey = "validation_name_invalid"),
            )
        }
        if (about.length > MAX_ABOUT_LENGTH) {
            return Result.failure(
                ChattlyError.Validation(field = "about", messageKey = "validation_about_too_long"),
            )
        }
        val normalizedUsername = username?.lowercase()?.trim()?.ifEmpty { null }
        if (normalizedUsername != null && !USERNAME_PATTERN.matches(normalizedUsername)) {
            return Result.failure(
                ChattlyError.Validation(field = "username", messageKey = "validation_username_invalid"),
            )
        }
        return profileRepository.updateProfile(name, normalizedUsername, about, avatarBlobId)
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
        const val MAX_ABOUT_LENGTH = 140
        val USERNAME_PATTERN = Regex("^[a-z0-9_]{3,32}$")
    }
}

/** AUTH-04: encrypts + uploads a raw avatar image (keys stay on device). */
class UploadAvatarUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(rawImage: ByteArray): Result<String> =
        profileRepository.uploadAvatar(rawImage)
}

/** AUTH-04: fetches an avatar and decrypts it for display. */
class FetchAvatarUseCase @Inject constructor(
    private val avatarImageSource: AvatarImageSource,
) {
    suspend operator fun invoke(blobId: String): Result<ByteArray> =
        avatarImageSource.fetchDecryptedAvatar(blobId)
}
