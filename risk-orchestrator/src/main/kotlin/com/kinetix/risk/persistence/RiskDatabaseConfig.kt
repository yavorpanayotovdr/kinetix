package com.kinetix.risk.persistence

import com.kinetix.common.persistence.ConnectionPoolConfig

data class RiskDatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val poolConfig: ConnectionPoolConfig = ConnectionPoolConfig.forService("risk-orchestrator"),
)
