package com.chattlyx.backend.redis

import redis.clients.jedis.JedisPool

/**
 * Per-recipient delivery queues (MSG-02). Redis holds envelope *ids* only —
 * durable envelope bodies live in Postgres; the queue is the fast fan-out
 * index. Sequence numbers are allocated here per conversation.
 */
class DeliveryQueues(private val pool: JedisPool) {

    /** Next monotonic seq for a conversation (INCR is atomic). */
    fun nextSeq(conversationId: String): Long = pool.resource.use { jedis ->
        jedis.incr(seqKey(conversationId))
    }

    /** Enqueue an envelope id for an account's devices. */
    fun enqueue(accountId: String, envelopeId: String) {
        pool.resource.use { jedis ->
            jedis.lpush(queueKey(accountId), envelopeId)
            jedis.ltrim(queueKey(accountId), 0, MAX_QUEUE_DEPTH - 1)
        }
    }

    /** Drains up to [limit] envelope ids (oldest first). */
    fun drain(accountId: String, limit: Int): List<String> {
        val out = mutableListOf<String>()
        pool.resource.use { jedis ->
            repeat(limit) {
                val next = jedis.rpop(queueKey(accountId)) ?: return@use
                out += next
            }
        }
        return out
    }

    fun pendingCount(accountId: String): Long = pool.resource.use { jedis ->
        jedis.llen(queueKey(accountId))
    }

    // --- Presence (MSG-05): online flag with auto-expiry. ---

    fun setOnline(accountId: String, ttlSeconds: Long) {
        pool.resource.use { jedis ->
            jedis.setex(presenceKey(accountId), ttlSeconds, "1")
        }
    }

    fun isOnline(accountId: String): Boolean = pool.resource.use { jedis ->
        jedis.exists(presenceKey(accountId))
    }

    fun lastSeenMillis(accountId: String): Long? = pool.resource.use { jedis ->
        jedis.get(lastSeenKey(accountId))?.toLongOrNull()
    }

    fun recordOffline(accountId: String, now: Long) {
        pool.resource.use { jedis ->
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
        const val MAX_QUEUE_DEPTH = 5_000
    }
}
