package com.chattlyx.backend.auth

/**
 * Sliding-window rate limiter (in-memory for Phase 1; Redis-backed for
 * multi-node scale-out in Phase 2). Keys are typically "otp:req:<e164Hash>",
 * "otp:req:ip:<addr>", "otp:verify:<e164Hash>".
 */
class RateLimiter(private val clock: () -> Long = System::currentTimeMillis) {

    private val windows = HashMap<String, ArrayDeque<Long>>()

    /** Returns null when allowed, otherwise milliseconds to wait. */
    fun tryAcquire(key: String, maxEvents: Int, windowMillis: Long): Long? = synchronized(this) {
        val now = clock()
        val deque = windows.getOrPut(key) { ArrayDeque() }

        while (deque.isNotEmpty() && deque.first() <= now - windowMillis) {
            deque.removeFirst()
        }

        if (deque.size >= maxEvents) {
            val oldest = deque.first()
            (oldest + windowMillis - now).coerceAtLeast(1)
        } else {
            deque.addLast(now)
            null
        }
    }

    /** Periodic eviction of stale keys; call from a background sweep. */
    fun evictStale(maxAgeMillis: Long = 3_600_000) = synchronized(this) {
        val now = clock()
        val iterator = windows.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.removeFirstWhile { it <= now - maxAgeMillis }
            if (entry.value.isEmpty()) iterator.remove()
        }
    }
}
