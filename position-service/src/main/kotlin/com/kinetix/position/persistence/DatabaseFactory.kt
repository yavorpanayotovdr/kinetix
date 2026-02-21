package com.kinetix.position.persistence

import com.kinetix.common.persistence.ConnectionPoolConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val poolConfig: ConnectionPoolConfig = ConnectionPoolConfig.forService("position-service"),
)

object DatabaseFactory {

    fun init(config: DatabaseConfig): Database {
        val dataSource = createDataSource(config)
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
            .locations("classpath:db/position")
            .load()
            .migrate()
    }
}
