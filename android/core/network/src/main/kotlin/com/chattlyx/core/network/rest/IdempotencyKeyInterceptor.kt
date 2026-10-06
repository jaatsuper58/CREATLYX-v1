package com.chattlyx.core.network.rest

import java.util.UUID
import okhttp3.Interceptor
import okhttp3.Response

/**
 * MASVS-RESILIENCE: stamps a per-request `Idempotency-Key` on the one
 * non-naturally-idempotent mutation (group creation). Application
 * interceptors run before OkHttp's retry machinery, so connection-level
 * retries reuse the exact same key and the server deduplicates them; a
 * fresh user attempt builds a new request and therefore a new key.
 */
class IdempotencyKeyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val needsKey = request.method == "POST" &&
            request.url.encodedPath.endsWith("/v1/groups") &&
            request.header("Idempotency-Key") == null
        val stamped = if (needsKey) {
            request.newBuilder()
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .build()
        } else {
            request
        }
        return chain.proceed(stamped)
    }
}
