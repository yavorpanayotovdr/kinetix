package com.kinetix.common.persistence

data class ConnectionPoolConfig(
    val maxPoolSize: Int = 10,
    val minIdle: Int = 2,
    val connectionTimeoutMs: Long = 30_000,
    val idleTimeoutMs: Long = 600_000,
    val maxLifetimeMs: Long = 1_800_000,
    val leakDetectionThresholdMs: Long = 60_000,
    val transactionIsolation: String = "TRANSACTION_REPEATABLE_READ",
    val autoCommit: Boolean = false,
) {
    init {
        require(maxPoolSize > 0) { "maxPoolSize must be > 0, was $maxPoolSize" }
        require(minIdle <= maxPoolSize) { "minIdle ($minIdle) must be <= maxPoolSize ($maxPoolSize)" }
        require(connectionTimeoutMs > 0) { "connectionTimeoutMs must be > 0, was $connectionTimeoutMs" }
    }

    companion object {
        fun forService(serviceName: String): ConnectionPoolConfig = when (serviceName) {
            "position-service" -> ConnectionPoolConfig(maxPoolSize = 15, minIdle = 3)
            "audit-service" -> ConnectionPoolConfig(maxPoolSize = 8, minIdle = 2)
            "market-data-service" -> ConnectionPoolConfig(maxPoolSize = 20, minIdle = 5)
            "notification-service" -> ConnectionPoolConfig(maxPoolSize = 8, minIdle = 2)
            "regulatory-service" -> ConnectionPoolConfig(maxPoolSize = 8, minIdle = 2)
            else -> ConnectionPoolConfig()
        }
    }
}
