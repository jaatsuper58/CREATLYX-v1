package com.chattlyx.data.messaging

import com.chattlyx.core.common.result.Result
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.safeCall
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches peer identity keys fetched from the AUTH-06 directory. Phase 2
 * talks to the first active device per peer (multi-device sessions arrive
 * with the libsignal integration).
 */
@Singleton
class PeerKeyResolverImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
) : PeerKeyResolver {

    private val cache = ConcurrentHashMap<String, String>()

    override suspend fun publicKeyFor(accountId: String): String? {
        cache[accountId]?.let { return it }

        val result = safeCall { api.keyBundles(accountId) }
        if (result !is Result.Success) return null

        val bundle = result.value.bundles.firstOrNull { !it.identityKey.isNullOrBlank() }
            ?: return null
        val key = bundle.identityKey.orEmpty()
        cache[accountId] = key
        return key
    }

    /** Identity key rotation invalidates cached sessions (Section 6.7). */
    fun invalidate(accountId: String) {
        cache.remove(accountId)
    }
}
