package com.kinetix.marketdata.persistence

import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object DatabaseTestSetup {

    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("timescale/timescaledb:latest-pg17")
            .asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("marketdata_test")
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
