package com.chattlyx.domain.auth

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.usecases.UpdateProfileUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

private class FakeProfileRepository : ProfileRepository {
    var lastUsername: String? = "sentinel"
    var lastAvatarBlobId: String? = "sentinel"

    override suspend fun getProfile(): Result<Profile> =
        Result.success(Profile("acct", "Asha", null, "", null))

    override suspend fun updateProfile(
        displayName: String,
        username: String?,
        about: String,
        avatarBlobId: String?,
    ): Result<Profile> {
        lastUsername = username
        lastAvatarBlobId = avatarBlobId
        return Result.success(Profile("acct", displayName, username, about, avatarBlobId))
    }

    override suspend fun uploadAvatar(rawImage: ByteArray): Result<String> = Result.success("blob")
}

class UpdateProfileUseCaseTest {

    private val repository = FakeProfileRepository()
    private val useCase = UpdateProfileUseCase(repository)

    @Test
    fun `valid profile passes through with normalised username`() = runTest {
        val result = useCase("  Aarav  ", "  Aarav_1 ", "hello")
        val success = assertIs<Result.Success<Profile>>(result)
        assertEquals("Aarav", success.value.displayName)
        assertEquals("aarav_1", repository.lastUsername)
    }

    @Test
    fun `blank username becomes null`() = runTest {
        useCase("Aarav", "   ", "")
        assertNull(repository.lastUsername)
    }

    @Test
    fun `empty name is rejected`() = runTest {
        val result = useCase("   ", null, "")
        assertIs<Result.Failure>(result)
    }

    @Test
    fun `name over 40 chars is rejected`() = runTest {
        val result = useCase("x".repeat(41), null, "")
        assertIs<Result.Failure>(result)
    }

    @Test
    fun `about over 140 chars is rejected`() = runTest {
        val result = useCase("Aarav", null, "a".repeat(141))
        assertIs<Result.Failure>(result)
    }

    @Test
    fun `username pattern violations are rejected`() = runTest {
        val result = useCase("Aarav", "no spaces", "")
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.Validation>(failure.error)
    }

    @Test
    fun `avatar blob id rides the profile update`() = runTest {
        useCase("Aarav", null, "", avatarBlobId = "blob-1")
        assertEquals("blob-1", repository.lastAvatarBlobId)
    }
}
