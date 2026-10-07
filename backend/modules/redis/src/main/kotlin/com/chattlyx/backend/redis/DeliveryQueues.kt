package com.chattlyx.backend.redis

import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

/**
 * Per-recipient delivery queues (MSG-02). Redis holds envelope *ids* only —
 * durable envelope bodies live in Postgres; the queue is the fast fan-out
 * index. Sequence numbers are allocated here per conversation.
 */
class DeliveryQueues(private val pool: JedisPool) {

    /** Borrows a connection; always returned to the pool. */
    private inline fun <T> withJedis(block: (Jedis) -> T): T {
        val jedis = pool.resource
        try {
            return block(jedis)
        } finally {
            jedis.close()
        }
    }

    /** Next monotonic seq for a conversation (INCR is atomic). */
    fun nextSeq(conversationId: String): Long = withJedis { jedis ->
        jedis.incr(seqKey(conversationId))
    }

    /** Enqueue an envelope id for an account's devices. */
    fun enqueue(accountId: String, envelopeId: String) {
        withJedis { jedis ->
            jedis.lpush(queueKey(accountId), envelopeId)
            jedis.ltrim(queueKey(accountId), 0L, MAX_QUEUE_DEPTH - 1L)
        }
    }

    /** Drains up to [limit] envelope ids (oldest first). */
    fun drain(accountId: String, limit: Int): List<String> {
        val out = mutableListOf<String>()
        withJedis { jedis ->
            repeat(limit) {
                val next = jedis.rpop(queueKey(accountId)) ?: return out
                out += next
            }
        }
        return out
    }

    fun pendingCount(accountId: String): Long = withJedis { jedis ->
        jedis.llen(queueKey(accountId))
    }

    // --- Presence (MSG-05): online flag with auto-expiry. ---

    fun setOnline(accountId: String, ttlSeconds: Long) {
        withJedis { jedis ->
            jedis.setex(presenceKey(accountId), ttlSeconds, "1")
        }
    }

    fun isOnline(accountId: String): Boolean = withJedis { jedis ->
        jedis.exists(presenceKey(accountId))
    }

    fun lastSeenMillis(accountId: String): Long? = withJedis { jedis ->
        jedis.get(lastSeenKey(accountId))?.toLongOrNull()
    }

    fun recordOffline(accountId: String, now: Long) {
        withJedis { jedis ->
            jedis.del(presenceKey(accountId))
            jedis.set(lastSeenKey(accountId), now.toString())
        }
    }

    private fun queueKey(accountId: String) = "cx:queue:$accountId"
    private fun seqKey(conversationId: String) = "cx:seq:$conversationId"
    private fun presenceKey(accountId: String) = "cx:online:$accountId"
    private fun lastSeenKey(accountId: String) = "cx:lastseen:$accountId"

    companion object {
        /** Bounded queue protects against offline backlog explosion. */
        const val MAX_QUEUE_DEPTH = 5_000L
    }
}
