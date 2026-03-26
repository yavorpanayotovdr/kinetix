package com.kinetix.risk.schedule

import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import org.slf4j.LoggerFactory

/**
 * Distributed lock abstraction for scheduled jobs.
 * Ensures only one instance of a job runs across replicas.
 */
interface DistributedLock {
    /**
     * Attempts to acquire the lock and execute [action] if successful.
     * The lock is released after [action] completes (or throws).
     * If the lock is already held, [action] is skipped.
     */
    suspend fun withLock(name: String, ttlSeconds: Long, action: suspend () -> Unit)
}

/**
 * Redis-based distributed lock using SET NX EX.
 * Safe for single-Redis deployments. The TTL prevents indefinite locks
 * if the holder crashes without releasing.
 */
class RedisDistributedLock(
    private val connection: StatefulRedisConnection<String, String>,
) : DistributedLock {

    private val logger = LoggerFactory.getLogger(RedisDistributedLock::class.java)
    private val sync = connection.sync()

    override suspend fun withLock(name: String, ttlSeconds: Long, action: suspend () -> Unit) {
        val key = "lock:$name"
        val result = sync.set(key, "1", SetArgs().nx().ex(ttlSeconds))
        if (result == null) {
            logger.debug("Lock {} held by another instance, skipping", name)
            return
        }
        try {
            action()
        } finally {
            sync.del(key)
        }
    }
}

/**
 * No-op lock that always executes the action.
 * Used when Redis is not available (single-instance dev mode).
 */
class NoOpDistributedLock : DistributedLock {
    override suspend fun withLock(name: String, ttlSeconds: Long, action: suspend () -> Unit) = action()
}
