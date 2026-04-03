package com.kinetix.common.resilience

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
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

    test("allows only halfOpenMaxCalls concurrent calls in HALF_OPEN state") {
        var now = Instant.parse("2025-01-15T10:00:00Z")
        val mutableClock = object : Clock() {
            override fun getZone() = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId?) = this
            override fun instant(): Instant = now
        }
        val cb = CircuitBreaker(
            CircuitBreakerConfig(failureThreshold = 2, resetTimeoutMs = 5000, halfOpenMaxCalls = 2),
            clock = mutableClock,
        )
        // Trip to OPEN
        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        cb.currentState shouldBe CircuitState.OPEN

        // Advance past reset timeout so next call transitions to HALF_OPEN
        now = now.plusMillis(6000)

        withContext(Dispatchers.Default) {
            // First call enters HALF_OPEN and blocks inside execute (simulating slow work)
            val gate = CompletableDeferred<Unit>()
            val firstCall = async {
                cb.execute {
                    gate.await() // block until we release
                    "first"
                }
            }

            // Give first coroutine time to acquire and release the mutex
            kotlinx.coroutines.delay(100)

            // Second call should also pass through (halfOpenMaxCalls = 2)
            val gate2 = CompletableDeferred<Unit>()
            val secondCall = async {
                cb.execute {
                    gate2.await()
                    "second"
                }
            }

            kotlinx.coroutines.delay(100)

            // Third call should be rejected because halfOpenMaxCalls = 2
            shouldThrow<CircuitBreakerOpenException> {
                cb.execute { "third" }
            }

            // Release both calls so they complete
            gate.complete(Unit)
            gate2.complete(Unit)
            firstCall.await() shouldBe "first"
            secondCall.await() shouldBe "second"
        }
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

    test("notifies state change listener on CLOSED to OPEN transition") {
        val transitions = mutableListOf<Pair<CircuitState, CircuitState>>()
        val cb = CircuitBreaker(
            CircuitBreakerConfig(failureThreshold = 2, name = "listener-test"),
            onStateChange = { old, new -> transitions.add(old to new) },
        )

        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }

        cb.currentState shouldBe CircuitState.OPEN
        transitions shouldContain (CircuitState.CLOSED to CircuitState.OPEN)
    }

    test("notifies state change listener on OPEN to HALF_OPEN and HALF_OPEN to CLOSED transitions") {
        val transitions = mutableListOf<Pair<CircuitState, CircuitState>>()
        var now = Instant.parse("2025-01-15T10:00:00Z")
        val mutableClock = object : Clock() {
            override fun getZone() = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId?) = this
            override fun instant(): Instant = now
        }
        val cb = CircuitBreaker(
            CircuitBreakerConfig(failureThreshold = 2, resetTimeoutMs = 5000, name = "listener-test"),
            clock = mutableClock,
            onStateChange = { old, new -> transitions.add(old to new) },
        )

        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        cb.currentState shouldBe CircuitState.OPEN

        now = now.plusMillis(6000)
        cb.execute { "recovered" }
        cb.currentState shouldBe CircuitState.CLOSED

        transitions shouldContain (CircuitState.CLOSED to CircuitState.OPEN)
        transitions shouldContain (CircuitState.OPEN to CircuitState.HALF_OPEN)
        transitions shouldContain (CircuitState.HALF_OPEN to CircuitState.CLOSED)
    }

    test("notifies state change listener on HALF_OPEN to OPEN re-trip") {
        val transitions = mutableListOf<Pair<CircuitState, CircuitState>>()
        var now = Instant.parse("2025-01-15T10:00:00Z")
        val mutableClock = object : Clock() {
            override fun getZone() = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId?) = this
            override fun instant(): Instant = now
        }
        val cb = CircuitBreaker(
            CircuitBreakerConfig(failureThreshold = 2, resetTimeoutMs = 5000, name = "listener-test"),
            clock = mutableClock,
            onStateChange = { old, new -> transitions.add(old to new) },
        )

        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        now = now.plusMillis(6000)
        runCatching { cb.execute { throw RuntimeException("still failing") } }

        transitions shouldContain (CircuitState.HALF_OPEN to CircuitState.OPEN)
    }
})
