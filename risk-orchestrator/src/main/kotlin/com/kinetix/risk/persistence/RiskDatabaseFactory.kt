package com.kinetix.risk.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object RiskDatabaseFactory {

    lateinit var dataSource: HikariDataSource
        private set

    const val FLYWAY_LOCATION = "classpath:db/risk"

    fun init(config: RiskDatabaseConfig): Database {
        dataSource = createDataSource(config)
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
            .locations(FLYWAY_LOCATION)
            .load()
            .migrate()
    }
}
