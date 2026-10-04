package com.chattlyx.core.crypto

/**
 * Cipher contracts (master spec Section 6.6). Implementations arrive with
 * their phases; interfaces are frozen now so storage, transport and UI code
 * can compile against them.
 */

/** 1:1 message encryption on top of established sessions. */
interface SessionCipher {
    suspend fun encrypt(accountId: String, deviceId: Int, plaintext: ByteArray): ByteArray
    suspend fun decrypt(accountId: String, deviceId: Int, ciphertext: ByteArray): ByteArray
}

/** Group message encryption via sender keys (rotated per membership change). */
interface GroupCipher {
    suspend fun encrypt(groupId: String, plaintext: ByteArray): ByteArray
    suspend fun decrypt(groupId: String, senderId: String, ciphertext: ByteArray): ByteArray

    /** Generates and distributes fresh sender keys after a membership change. */
    suspend fun rotateSenderKey(groupId: String, memberIds: List<String>)
}

/** 60-digit safety number + QR payload generation (Section 9.1). */
interface SafetyNumberGenerator {
    suspend fun safetyNumber(localAccountId: String, peerAccountId: String): String
    suspend fun qrPayload(localAccountId: String, peerAccountId: String): ByteArray
}

/** E2EE backup encryption (BKP-01/02): Argon2id passphrase -> AES-256-GCM. */
interface BackupCipher {
    suspend fun encryptBackup(plaintext: ByteArray, passphrase: CharArray): ByteArray
    suspend fun decryptBackup(ciphertext: ByteArray, passphrase: CharArray): ByteArray
}
