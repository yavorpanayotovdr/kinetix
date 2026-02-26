package com.kinetix.correlation.persistence

import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.PostgreSQLContainer

object DatabaseTestSetup {

    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("correlation_test")
        .withUsername("test")
        .withPassword("test")

    fun startAndMigrate(): Database {
        if (!postgres.isRunning) {
            postgres.start()
        }
        return DatabaseFactory.init(
            DatabaseConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
                maxPoolSize = 5,
            )
        )
    }
}
