package com.chattlyx.core.crypto

/**
 * Signal-protocol storage contracts (master spec Section 6.6). The concrete
 * engine (libsignal per ADR-0002, pending licence gate) plugs into these
 * interfaces in Phase 2; nothing above this layer references the engine.
 *
 * All values MUST be stored encrypted at rest with the Keystore-wrapped
 * master key (see KeystoreKeyWrapper) — the database module enforces this.
 */

/** Long-term identity key pair per device. Private material never leaves the device. */
interface IdentityKeyStore {
    suspend fun identityKeyPair(): ByteArray
    suspend fun localRegistrationId(): Int

    /** Records a peer identity key; returns false when it CHANGED (KeyChanged flow). */
    suspend fun saveIdentity(accountId: String, deviceId: Int, identityKey: ByteArray): Boolean
    suspend fun identityFor(accountId: String, deviceId: Int): ByteArray?
    suspend fun isTrusted(accountId: String, deviceId: Int, identityKey: ByteArray): Boolean
}

/** One-time, signed and post-quantum prekeys for asynchronous setup (AUTH-06). */
interface PreKeyStore {
    suspend fun storeOneTimePreKey(preKeyId: Int, record: ByteArray)
    suspend fun oneTimePreKey(preKeyId: Int): ByteArray?
    suspend fun removeOneTimePreKey(preKeyId: Int)

    suspend fun storeSignedPreKey(signedPreKeyId: Int, record: ByteArray)
    suspend fun signedPreKey(signedPreKeyId: Int): ByteArray?

    suspend fun storeKyberPreKey(kyberPreKeyId: Int, record: ByteArray)
    suspend fun kyberPreKey(kyberPreKeyId: Int): ByteArray?
    suspend fun removeKyberPreKey(kyberPreKeyId: Int)

    /** Client checks this and replenishes below the threshold (AUTH-06: 20). */
    suspend fun remainingOneTimePreKeys(): Int
}

/** Double Ratchet session state per peer device. One mutex per session. */
interface SessionStore {
    suspend fun storeSession(accountId: String, deviceId: Int, record: ByteArray)
    suspend fun session(accountId: String, deviceId: Int): ByteArray?
    suspend fun containsSession(accountId: String, deviceId: Int): Boolean
    suspend fun sessionsFor(accountId: String): List<Int>
    suspend fun deleteSession(accountId: String, deviceId: Int)
}

/** Sender-key records for group E2EE (GRP-03), rotated on membership change. */
interface SenderKeyStore {
    suspend fun storeSenderKey(groupId: String, senderId: String, record: ByteArray)
    suspend fun senderKey(groupId: String, senderId: String): ByteArray?
}
