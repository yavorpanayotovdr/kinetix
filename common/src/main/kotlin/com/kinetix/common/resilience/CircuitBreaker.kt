package com.kinetix.common.resilience

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val resetTimeoutMs: Long = 30_000,
    val halfOpenMaxCalls: Int = 1,
    val name: String = "default",
)

class CircuitBreakerOpenException(val circuitName: String) :
    RuntimeException("Circuit breaker '$circuitName' is OPEN")

class CircuitBreaker(
    private val config: CircuitBreakerConfig = CircuitBreakerConfig(),
    private val clock: Clock = Clock.systemUTC(),
    private val onStateChange: ((old: CircuitState, new: CircuitState) -> Unit)? = null,
) {
    private val logger = LoggerFactory.getLogger(CircuitBreaker::class.java)
    private val mutex = Mutex()
    private var state: CircuitState = CircuitState.CLOSED
    private var failureCount: Int = 0
    private var lastFailureTime: Instant? = null
    private var halfOpenCalls: Int = 0

    val currentState: CircuitState get() = state

    suspend fun <T> execute(block: suspend () -> T): T {
        mutex.withLock {
            when (state) {
                CircuitState.OPEN -> {
                    val elapsed = lastFailureTime?.let {
                        clock.millis() - it.toEpochMilli()
                    } ?: Long.MAX_VALUE
                    if (elapsed >= config.resetTimeoutMs) {
                        transitionTo(CircuitState.HALF_OPEN)
                        halfOpenCalls = 1
                    } else {
                        throw CircuitBreakerOpenException(config.name)
                    }
                }
                CircuitState.HALF_OPEN -> {
                    if (halfOpenCalls >= config.halfOpenMaxCalls) {
                        throw CircuitBreakerOpenException(config.name)
                    }
                    halfOpenCalls++
                }
                CircuitState.CLOSED -> { /* allow */ }
            }
        }

        return try {
            val result = block()
            mutex.withLock { onSuccess() }
            result
        } catch (e: Exception) {
            mutex.withLock { onFailure() }
            throw e
        }
    }

    private fun transitionTo(newState: CircuitState) {
        val oldState = state
        state = newState
        logger.info(
            "Circuit breaker '{}' transitioned: {} -> {} (failures={})",
            config.name, oldState, newState, failureCount,
        )
        onStateChange?.invoke(oldState, newState)
    }

    private fun onSuccess() {
        when (state) {
            CircuitState.HALF_OPEN -> {
                transitionTo(CircuitState.CLOSED)
                failureCount = 0
                halfOpenCalls = 0
            }
            CircuitState.CLOSED -> {
                failureCount = 0
            }
            else -> { /* shouldn't happen */ }
        }
    }

    private fun onFailure() {
        when (state) {
            CircuitState.CLOSED -> {
                failureCount++
                if (failureCount >= config.failureThreshold) {
                    lastFailureTime = clock.instant()
                    transitionTo(CircuitState.OPEN)
                }
            }
            CircuitState.HALF_OPEN -> {
                lastFailureTime = clock.instant()
                transitionTo(CircuitState.OPEN)
            }
            else -> { /* already open */ }
        }
    }

    suspend fun reset() {
        mutex.withLock {
            state = CircuitState.CLOSED
            failureCount = 0
            halfOpenCalls = 0
            lastFailureTime = null
        }
    }
}
