package com.kinetix.risk.resilience

import com.kinetix.common.resilience.CircuitBreaker
import com.kinetix.common.resilience.CircuitBreakerConfig
import com.kinetix.common.resilience.CircuitBreakerOpenException
import com.kinetix.common.resilience.CircuitState
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration test for [CircuitBreaker] operating against a real HTTP server.
 *
 * Unlike the unit tests in common which use simulated exceptions, this test
 * verifies that the circuit breaker state machine holds under actual network
 * I/O conditions — real TCP connections, real HTTP responses, and real
 * connection errors from a controllable embedded server.
 */
class CircuitBreakerHttpIntegrationTest : FunSpec({

    lateinit var server: HttpServer
    var serverPort: Int = 0
    val statusCode = AtomicInteger(200)

    beforeEach {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/health") { exchange ->
            val code = statusCode.get()
            val body = if (code == 200) "OK" else "ERROR"
            exchange.sendResponseHeaders(code, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        serverPort = server.address.port
        statusCode.set(200)
    }

    afterEach {
        server.stop(0)
    }

    test("circuit stays CLOSED when HTTP calls succeed") {
        val cb = CircuitBreaker(CircuitBreakerConfig(failureThreshold = 3, name = "http-test"))
        val client = HttpClient(CIO)

        repeat(5) {
            val response = cb.execute {
                client.get("http://localhost:$serverPort/health")
            }
            response.status.value shouldBe 200
        }
        cb.currentState shouldBe CircuitState.CLOSED

        client.close()
    }

    test("circuit opens after failure threshold from HTTP 500 responses") {
        val cb = CircuitBreaker(CircuitBreakerConfig(failureThreshold = 3, name = "http-500-test"))
        val client = HttpClient(CIO)

        statusCode.set(500)

        repeat(3) {
            runCatching {
                cb.execute {
                    val response = client.get("http://localhost:$serverPort/health")
                    if (response.status.value >= 500) {
                        throw RuntimeException("Server error: ${response.status.value}")
                    }
                    response
                }
            }
        }
        cb.currentState shouldBe CircuitState.OPEN

        shouldThrow<CircuitBreakerOpenException> {
            cb.execute {
                client.get("http://localhost:$serverPort/health")
            }
        }

        client.close()
    }

    test("circuit opens after connection refused errors") {
        val cb = CircuitBreaker(CircuitBreakerConfig(failureThreshold = 2, name = "conn-refused-test"))
        val client = HttpClient(CIO)

        // Stop the server to produce real connection refused errors
        server.stop(0)

        repeat(2) {
            runCatching {
                cb.execute {
                    client.get("http://localhost:$serverPort/health")
                }
            }
        }
        cb.currentState shouldBe CircuitState.OPEN

        client.close()
    }

    test("full lifecycle: CLOSED -> OPEN -> HALF_OPEN -> CLOSED with real HTTP calls") {
        var now = Instant.parse("2025-01-15T10:00:00Z")
        val mutableClock = object : Clock() {
            override fun getZone(): ZoneId = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId?): Clock = this
            override fun instant(): Instant = now
        }

        val cb = CircuitBreaker(
            CircuitBreakerConfig(failureThreshold = 2, resetTimeoutMs = 3000, name = "lifecycle-test"),
            clock = mutableClock,
        )
        val client = HttpClient(CIO)

        // Phase 1: CLOSED — successful calls
        val response1 = cb.execute { client.get("http://localhost:$serverPort/health") }
        response1.status.value shouldBe 200
        cb.currentState shouldBe CircuitState.CLOSED

        // Phase 2: Failures trip the circuit OPEN
        statusCode.set(500)
        repeat(2) {
            runCatching {
                cb.execute {
                    val r = client.get("http://localhost:$serverPort/health")
                    if (r.status.value >= 500) throw RuntimeException("HTTP ${r.status.value}")
                    r
                }
            }
        }
        cb.currentState shouldBe CircuitState.OPEN

        // Phase 3: Calls rejected while OPEN
        shouldThrow<CircuitBreakerOpenException> {
            cb.execute { client.get("http://localhost:$serverPort/health") }
        }

        // Phase 4: After reset timeout, transitions to HALF_OPEN
        now = now.plusMillis(4000)
        statusCode.set(200)

        // The next call triggers HALF_OPEN and succeeds, closing the circuit
        val response2 = cb.execute { client.get("http://localhost:$serverPort/health") }
        response2.status.value shouldBe 200
        cb.currentState shouldBe CircuitState.CLOSED

        // Phase 5: Circuit is fully recovered — calls pass through again
        val response3 = cb.execute { client.get("http://localhost:$serverPort/health") }
        response3.status.value shouldBe 200
        cb.currentState shouldBe CircuitState.CLOSED

        client.close()
    }

    test("half-open failure from real HTTP error re-opens circuit") {
        var now = Instant.parse("2025-01-15T10:00:00Z")
        val mutableClock = object : Clock() {
            override fun getZone(): ZoneId = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId?): Clock = this
            override fun instant(): Instant = now
        }

        val cb = CircuitBreaker(
            CircuitBreakerConfig(failureThreshold = 2, resetTimeoutMs = 3000, name = "half-open-fail-test"),
            clock = mutableClock,
        )
        val client = HttpClient(CIO)

        // Trip the circuit open
        statusCode.set(500)
        repeat(2) {
            runCatching {
                cb.execute {
                    val r = client.get("http://localhost:$serverPort/health")
                    if (r.status.value >= 500) throw RuntimeException("HTTP ${r.status.value}")
                    r
                }
            }
        }
        cb.currentState shouldBe CircuitState.OPEN

        // Advance past reset timeout — circuit moves to HALF_OPEN
        now = now.plusMillis(4000)

        // Server still returning 500 — HALF_OPEN call fails, circuit re-opens
        runCatching {
            cb.execute {
                val r = client.get("http://localhost:$serverPort/health")
                if (r.status.value >= 500) throw RuntimeException("HTTP ${r.status.value}")
                r
            }
        }
        cb.currentState shouldBe CircuitState.OPEN

        client.close()
    }
})
