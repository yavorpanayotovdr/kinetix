package com.kinetix.correlation.persistence

import com.kinetix.common.persistence.ConnectionPoolConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.MeterRegistry
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val poolConfig: ConnectionPoolConfig = ConnectionPoolConfig.forService("correlation-service"),
)

object DatabaseFactory {

    fun init(config: DatabaseConfig, meterRegistry: MeterRegistry? = null): Database {
        val dataSource = createDataSource(config)
        meterRegistry?.let { dataSource.metricsTrackerFactory = com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory(it) }
        runMigrations(dataSource)
        return Database.connect(dataSource)
    }

    private fun createDataSource(config: DatabaseConfig): HikariDataSource {
        val pool = config.poolConfig
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = pool.maxPoolSize
            minimumIdle = pool.minIdle
            connectionTimeout = pool.connectionTimeoutMs
            idleTimeout = pool.idleTimeoutMs
            maxLifetime = pool.maxLifetimeMs
            leakDetectionThreshold = pool.leakDetectionThresholdMs
            isAutoCommit = pool.autoCommit
            transactionIsolation = pool.transactionIsolation
            validate()
        }
        return HikariDataSource(hikariConfig)
    }

    private fun runMigrations(dataSource: HikariDataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/correlation")
            .load()
            .migrate()
    }
}
