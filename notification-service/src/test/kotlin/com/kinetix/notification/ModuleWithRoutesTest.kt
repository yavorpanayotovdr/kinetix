package com.kinetix.notification

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*

class ModuleWithRoutesTest : FunSpec({

    test("moduleWithRoutes loads health route") {
        testApplication {
            application { moduleWithRoutes() }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"UP"}"""
        }
    }

    test("moduleWithRoutes loads notification rules route") {
        testApplication {
            application { moduleWithRoutes() }
            val response = client.get("/api/v1/notifications/rules")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    test("moduleWithRoutes loads notification alerts route") {
        testApplication {
            application { moduleWithRoutes() }
            val response = client.get("/api/v1/notifications/alerts")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }
})
