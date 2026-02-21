package com.kinetix.gateway.ratelimit

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

data class RateLimiterConfig(
    val requestsPerSecond: Int = 100,
    val burstSize: Int = 150,
    val excludedPaths: Set<String> = setOf("/health", "/metrics"),
)

class TokenBucketRateLimiter(
    private val config: RateLimiterConfig = RateLimiterConfig(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val buckets = ConcurrentHashMap<String, TokenBucket>()

    fun tryAcquire(clientId: String): Boolean {
        val bucket = buckets.computeIfAbsent(clientId) {
            TokenBucket(config.burstSize, config.requestsPerSecond, clock)
        }
        return bucket.tryConsume()
    }

    private class TokenBucket(
        private val capacity: Int,
        private val refillRate: Int,
        private val clock: Clock,
    ) {
        private var tokens: Double = capacity.toDouble()
        private var lastRefillTime: Long = clock.millis()

        @Synchronized
        fun tryConsume(): Boolean {
            refill()
            return if (tokens >= 1.0) {
                tokens -= 1.0
                true
            } else {
                false
            }
        }

        private fun refill() {
            val now = clock.millis()
            val elapsed = (now - lastRefillTime) / 1000.0
            tokens = minOf(capacity.toDouble(), tokens + elapsed * refillRate)
            lastRefillTime = now
        }
    }
}
