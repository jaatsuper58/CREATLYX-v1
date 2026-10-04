package com.chattlyx.backend.redis

import java.time.Duration
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

/** Redis connection factory (delivery queues + presence, MSG-02/05). */
object RedisFactory {

    fun create(config: RedisConfig): JedisPool {
        val poolConfig = JedisPoolConfig().apply {
            maxTotal = config.maxPoolSize
            testOnBorrow = false
            testWhileIdle = true
        }
        return JedisPool(poolConfig, config.host, config.port, config.timeout, config.password)
    }
}

data class RedisConfig(
    val host: String = "localhost",
    val port: Int = 6379,
    val password: String? = null,
    val timeout: Duration = Duration.ofSeconds(2),
    val maxPoolSize: Int = 10,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): RedisConfig = RedisConfig(
            host = env["CHATTLYX_REDIS_HOST"] ?: "localhost",
            port = env["CHATTLYX_REDIS_PORT"]?.toIntOrNull() ?: 6379,
            password = env["CHATTLYX_REDIS_PASSWORD"],
        )
    }
}
