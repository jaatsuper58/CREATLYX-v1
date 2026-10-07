package com.chattlyx.app.integrity

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * SAF hardening (Phase 7): Play Integrity device verdicts.
 *
 * The provider hands back a signed integrity token for a caller-supplied
 * nonce; server-side decoding (Google's Play Integrity decode endpoint,
 * which needs the linked Google Cloud project) decides enforcement. Tokens
 * are single-use — request a fresh one per sensitive mutation, never cache.
 * Builds without a configured GCP project bind [NoopDeviceIntegrityProvider].
 */
interface DeviceIntegrityProvider {

    /** True when real Play Integrity verdicts are wired into this build. */
    val isEnabled: Boolean

    /**
     * Requests a fresh integrity token. [nonceBase64] must be URL-safe
     * base64 without padding (16..10_000 bytes decoded). Returns null when
     * the verdict is unavailable (no Play Store, unsupported device, or the
     * provider is disabled) — callers fail open/closed per their own policy.
     */
    suspend fun requestToken(nonceBase64: String): String?
}

/** Default binding: integrity checks are not wired in this build. */
class NoopDeviceIntegrityProvider : DeviceIntegrityProvider {

    override val isEnabled: Boolean = false

    override suspend fun requestToken(nonceBase64: String): String? = null
}

/**
 * Real Play Integrity binding. [cloudProjectNumber] is the numeric GCP
 * project id linked to the Play Console app (Play Console → App integrity).
 */
class PlayDeviceIntegrityProvider(
    private val integrityManager: IntegrityManager,
    private val cloudProjectNumber: Long,
    private val ioDispatcher: CoroutineDispatcher,
) : DeviceIntegrityProvider {

    override val isEnabled: Boolean = true

    override suspend fun requestToken(nonceBase64: String): String? = withContext(ioDispatcher) {
        val request = IntegrityTokenRequest.builder()
            .setNonce(nonceBase64)
            .setCloudProjectNumber(cloudProjectNumber)
            .build()
        runCatching {
            suspendCancellableCoroutine { continuation ->
                integrityManager.requestIntegrityToken(request)
                    .addOnSuccessListener { response ->
                        continuation.resumeWith(Result.success(response.token()))
                    }
                    .addOnFailureListener { error ->
                        // Never log the nonce or any verdict detail.
                        Timber.w(error, "Play Integrity token request failed")
                        continuation.resumeWith(Result.success(null))
                    }
            }
        }.getOrNull()
    }
}

/** Builds the binding from build-time configuration; null = noop. */
internal fun createDeviceIntegrityProvider(
    context: Context,
    enabled: Boolean,
    cloudProjectNumber: Long,
    ioDispatcher: CoroutineDispatcher,
): DeviceIntegrityProvider {
    if (!enabled) return NoopDeviceIntegrityProvider()
    if (cloudProjectNumber <= 0L) {
        // Misconfiguration: enabled without a linked GCP project. Stay
        // disabled rather than requesting tokens that cannot decode.
        Timber.w("Play Integrity enabled but no cloud project number configured")
        return NoopDeviceIntegrityProvider()
    }
    return PlayDeviceIntegrityProvider(
        integrityManager = IntegrityManagerFactory.create(context),
        cloudProjectNumber = cloudProjectNumber,
        ioDispatcher = ioDispatcher,
    )
}

/** URL-safe base64 nonces per the Play Integrity requirements. */
object IntegrityNonce {

    /** Generates a 32-byte random nonce (URL-safe base64, no padding). */
    fun generate(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
