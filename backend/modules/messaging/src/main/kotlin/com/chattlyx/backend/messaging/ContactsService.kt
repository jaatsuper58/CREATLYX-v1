package com.chattlyx.backend.messaging

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.db.AccountRepository

/**
 * Private contact discovery (CON-03): clients send unpeppered SHA-256
 * hashes of E.164 numbers; the server answers with public profile fields
 * only. Raw numbers never travel and are never returned.
 */
class ContactsService(
    private val accounts: AccountRepository,
) {

    data class DiscoveredContact(
        val accountId: java.util.UUID,
        val displayName: String,
        val username: String?,
        val avatarBlobId: java.util.UUID?,
    )

    fun discover(hashes: List<String>): List<DiscoveredContact> {
        if (hashes.size > MAX_HASHES_PER_REQUEST) {
            throw ChattlyxServerException.Validation("at most $MAX_HASHES_PER_REQUEST hashes per request")
        }
        val normalized = hashes.map { it.trim().lowercase() }.distinct().toSet()
        if (normalized.any { !SHA256_HEX.matches(it) }) {
            throw ChattlyxServerException.Validation("hashes must be lowercase hex SHA-256")
        }

        return accounts.findDiscoveryMatches(normalized).map { row ->
            DiscoveredContact(
                accountId = row.id,
                displayName = row.displayName,
                username = row.username,
                avatarBlobId = row.avatarBlobId,
            )
        }
    }

    companion object {
        const val MAX_HASHES_PER_REQUEST = 1_000
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}
