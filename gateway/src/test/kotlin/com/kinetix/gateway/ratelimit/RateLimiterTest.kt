package com.kinetix.gateway.ratelimit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class RateLimiterTest : FunSpec({

    test("RateLimiterConfig has sensible defaults") {
        val config = RateLimiterConfig()
        config.requestsPerSecond shouldBe 100
        config.burstSize shouldBe 150
        config.excludedPaths shouldBe setOf("/health", "/metrics")
    }

    test("allows requests within limit") {
        val config = RateLimiterConfig(requestsPerSecond = 10, burstSize = 5)
        val limiter = TokenBucketRateLimiter(config)
        repeat(5) { i ->
            limiter.tryAcquire("client-1") shouldBe true
        }
    }

    test("rejects requests exceeding limit") {
        val config = RateLimiterConfig(requestsPerSecond = 10, burstSize = 3)
        val limiter = TokenBucketRateLimiter(config)
        repeat(3) { limiter.tryAcquire("client-1") }
        limiter.tryAcquire("client-1") shouldBe false
    }

    test("refills tokens after interval") {
        var now = Instant.parse("2025-01-15T10:00:00Z")
        val mutableClock = object : Clock() {
            override fun getZone() = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId?) = this
            override fun instant(): Instant = now
            override fun millis(): Long = now.toEpochMilli()
        }
        val config = RateLimiterConfig(requestsPerSecond = 10, burstSize = 3)
        val limiter = TokenBucketRateLimiter(config, mutableClock)

        repeat(3) { limiter.tryAcquire("client-1") }
        limiter.tryAcquire("client-1") shouldBe false

        now = now.plusMillis(1000)
        limiter.tryAcquire("client-1") shouldBe true
    }

    test("tracks requests per client IP") {
        val config = RateLimiterConfig(requestsPerSecond = 10, burstSize = 2)
        val limiter = TokenBucketRateLimiter(config)

        repeat(2) { limiter.tryAcquire("client-1") }
        limiter.tryAcquire("client-1") shouldBe false

        limiter.tryAcquire("client-2") shouldBe true
        limiter.tryAcquire("client-2") shouldBe true
        limiter.tryAcquire("client-2") shouldBe false
    }
})
