package com.chattlyx.data.auth

import com.chattlyx.domain.auth.OwnKeyBundle
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates this device's public key material for AUTH-06 upload.
 *
 * LICENCE GATE (ADR-0002): the production protocol is PQXDH via libsignal
 * (X25519 + Ed25519 + Kyber-1024). libsignal is AGPL-3.0 and under counsel
 * review, so Phase 1 ships this JCA placeholder: an X25519 identity
 * agreement key (EC P-256 fallback on API < 26) and deterministic prekey
 * records derived from fresh key pairs. The server treats every `record`
 * field as opaque bytes, so swapping in real libsignal records requires no
 * schema or API change — only this class. Kyber prekeys are generated as
 * empty sets until the PQ library lands; the server accepts zero Kyber keys.
 */
@Singleton
class DeviceKeyGenerator @Inject constructor() {

    /** Identity key pair lives in memory for the process lifetime (Phase 1). */
    @Volatile
    private var identityKeyPair: KeyPair? = null

    private val encoder = Base64.getEncoder()

    /** Returns (identityPublicB64, signedPrekeyId, signedPrekeyRecordB64). */
    fun generateIdentity(): Pair<String, Pair<Int, String>> {
        val pair = identityKeyPair ?: generateAgreementKeyPair().also { identityKeyPair = it }
        val publicB64 = encoder.encodeToString(pair.public.encoded)
        // Signed prekey placeholder: a second key pair, signed later by libsignal.
        val signedPair = generateAgreementKeyPair()
        val record = encodeRecord(prekeyId = 1, publicKey = signedPair.public.encoded)
        return publicB64 to (1 to record)
    }

    /** Generates a batch of one-time prekey records (opaque on the server). */
    fun generateOneTimePrekeys(startId: Int, count: Int): List<Pair<Int, String>> =
        (0 until count).map { index ->
            val id = startId + index
            val pair = generateAgreementKeyPair()
            id to encodeRecord(prekeyId = id, publicKey = pair.public.encoded)
        }

    /** Builds the full upload bundle for registration or replenishment. */
    fun buildBundle(oneTimePrekeyCount: Int = ONE_TIME_PREKEY_BATCH): OwnKeyBundle {
        val (identity, signed) = generateIdentity()
        return OwnKeyBundle(
            identityKey = identity,
            signedPrekeyId = signed.first,
            signedPrekeyRecord = signed.second,
            oneTimePrekeys = generateOneTimePrekeys(startId = oneTimeNextId, count = oneTimePrekeyCount)
                .also { oneTimeNextId += oneTimePrekeyCount },
            kyberPrekeys = emptyList(), // PQ keys arrive with the libsignal decision (ADR-0002)
        )
    }

    private var oneTimeNextId = 100

    private fun generateAgreementKeyPair(): KeyPair =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            KeyPairGenerator.getInstance("XDH").apply {
                initialize(java.security.spec.NamedParameterSpec.X25519)
            }.generateKeyPair()
        } else {
            KeyPairGenerator.getInstance("EC").apply {
                initialize(256) // P-256 fallback for API 24/25
            }.generateKeyPair()
        }

    /**
     * Placeholder record layout: [0x01][varint prekeyId][X509 public key].
     * Opaque to the server; replaced wholesale by libsignal serialization.
     */
    private fun encodeRecord(prekeyId: Int, publicKey: ByteArray): String {
        val bytes = ByteArray(5 + publicKey.size)
        bytes[0] = 0x01
        bytes[1] = (prekeyId shr 24).toByte()
        bytes[2] = (prekeyId shr 16).toByte()
        bytes[3] = (prekeyId shr 8).toByte()
        bytes[4] = prekeyId.toByte()
        publicKey.copyInto(bytes, 5)
        return encoder.encodeToString(bytes)
    }

    private companion object {
        const val ONE_TIME_PREKEY_BATCH = 50
    }
}
