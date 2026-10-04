package com.chattlyx.backend.messaging

import java.util.UUID

/**
 * Canonical 1:1 conversation id: `dm:` + sorted account uuids. Both clients
 * and the server derive the same value, so no conversation table is needed.
 */
object ConversationIds {

    fun direct(a: UUID, b: UUID): String {
        val (first, second) = if (a.toString() <= b.toString()) a to b else b to a
        return "dm:$first:$second"
    }

    fun participants(conversationId: String): Pair<UUID, UUID>? {
        val parts = conversationId.split(':')
        if (parts.size != 3 || parts[0] != "dm") return null
        return try {
            UUID.fromString(parts[1]) to UUID.fromString(parts[2])
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
