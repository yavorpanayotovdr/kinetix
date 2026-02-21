package com.kinetix.common.resilience

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CircuitBreakerTest : FunSpec({

    test("circuit breaker starts in CLOSED state") {
        val cb = CircuitBreaker()
        cb.currentState shouldBe CircuitState.CLOSED
    }

    test("successful calls pass through in CLOSED state") {
        val cb = CircuitBreaker()
        val result = cb.execute { "ok" }
        result shouldBe "ok"
        cb.currentState shouldBe CircuitState.CLOSED
    }

    test("failures increment failure count") {
        val cb = CircuitBreaker(CircuitBreakerConfig(failureThreshold = 3))
        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        cb.currentState shouldBe CircuitState.CLOSED
    }

    test("circuit opens after failure threshold reached") {
        val cb = CircuitBreaker(CircuitBreakerConfig(failureThreshold = 3))
        repeat(3) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        cb.currentState shouldBe CircuitState.OPEN
    }

    test("calls rejected in OPEN state with CircuitBreakerOpenException") {
        val cb = CircuitBreaker(CircuitBreakerConfig(failureThreshold = 2))
        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        cb.currentState shouldBe CircuitState.OPEN

        val ex = shouldThrow<CircuitBreakerOpenException> {
            cb.execute { "should not run" }
        }
        ex.circuitName shouldBe "default"
    }

    test("circuit transitions to HALF_OPEN after reset timeout") {
        var now = Instant.parse("2025-01-15T10:00:00Z")
        val clock = Clock.fixed(now, ZoneId.of("UTC"))
        val mutableClock = object : Clock() {
            override fun getZone() = clock.zone
            override fun withZone(zone: ZoneId?) = this
            override fun instant(): Instant = now
        }
        val cb = CircuitBreaker(
            CircuitBreakerConfig(failureThreshold = 2, resetTimeoutMs = 5000, name = "test"),
            clock = mutableClock,
        )
        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        cb.currentState shouldBe CircuitState.OPEN

        now = now.plusMillis(6000)
        cb.execute { "recovered" } shouldBe "recovered"
        cb.currentState shouldBe CircuitState.CLOSED
    }

    test("successful call in HALF_OPEN closes circuit") {
        var now = Instant.parse("2025-01-15T10:00:00Z")
        val mutableClock = object : Clock() {
            override fun getZone() = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId?) = this
            override fun instant(): Instant = now
        }
        val cb = CircuitBreaker(
            CircuitBreakerConfig(failureThreshold = 2, resetTimeoutMs = 5000),
            clock = mutableClock,
        )
        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        cb.currentState shouldBe CircuitState.OPEN

        now = now.plusMillis(6000)
        val result = cb.execute { "success" }
        result shouldBe "success"
        cb.currentState shouldBe CircuitState.CLOSED
    }

    test("failed call in HALF_OPEN re-opens circuit") {
        var now = Instant.parse("2025-01-15T10:00:00Z")
        val mutableClock = object : Clock() {
            override fun getZone() = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId?) = this
            override fun instant(): Instant = now
        }
        val cb = CircuitBreaker(
            CircuitBreakerConfig(failureThreshold = 2, resetTimeoutMs = 5000),
            clock = mutableClock,
        )
        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        cb.currentState shouldBe CircuitState.OPEN

        now = now.plusMillis(6000)
        runCatching { cb.execute { throw RuntimeException("still failing") } }
        cb.currentState shouldBe CircuitState.OPEN
    }
})
