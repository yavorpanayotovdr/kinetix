package com.kinetix.risk.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object RiskDatabaseFactory {

    fun init(config: RiskDatabaseConfig): Database {
        val dataSource = createDataSource(config)
        runMigrations(dataSource)
        return Database.connect(dataSource)
    }

    private fun createDataSource(config: RiskDatabaseConfig): HikariDataSource {
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
            .locations("classpath:db/risk")
            .load()
            .migrate()
    }
}
