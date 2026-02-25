package com.kinetix.audit

import com.kinetix.audit.persistence.DatabaseTestSetup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*

class ModuleWithRoutesTest : FunSpec({

    beforeSpec {
        DatabaseTestSetup.startAndMigrate()
    }

    test("moduleWithRoutes loads health route") {
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "database.jdbcUrl" to DatabaseTestSetup.postgres.jdbcUrl,
                    "database.username" to DatabaseTestSetup.postgres.username,
                    "database.password" to DatabaseTestSetup.postgres.password,
                    "kafka.bootstrapServers" to "localhost:9092",
                    "seed.enabled" to "false",
                )
            }
            application { moduleWithRoutes() }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"UP"}"""
        }
    }

    test("moduleWithRoutes loads audit events route") {
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "database.jdbcUrl" to DatabaseTestSetup.postgres.jdbcUrl,
                    "database.username" to DatabaseTestSetup.postgres.username,
                    "database.password" to DatabaseTestSetup.postgres.password,
                    "kafka.bootstrapServers" to "localhost:9092",
                    "seed.enabled" to "false",
                )
            }
            application { moduleWithRoutes() }
            val response = client.get("/api/v1/audit/events")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }
})
