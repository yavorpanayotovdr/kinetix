package com.kinetix.notification

import com.kinetix.notification.delivery.InAppDeliveryService
import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.persistence.InMemoryAlertEventRepository
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*

class ModuleWithRoutesTest : FunSpec({

    test("module loads health route") {
        testApplication {
            application {
                module(
                    RulesEngine(InMemoryAlertRuleRepository()),
                    InAppDeliveryService(InMemoryAlertEventRepository()),
                )
            }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"UP"}"""
        }
    }

    test("module loads notification rules route") {
        testApplication {
            application {
                module(
                    RulesEngine(InMemoryAlertRuleRepository()),
                    InAppDeliveryService(InMemoryAlertEventRepository()),
                )
            }
            val response = client.get("/api/v1/notifications/rules")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    test("module loads notification alerts route") {
        testApplication {
            application {
                module(
                    RulesEngine(InMemoryAlertRuleRepository()),
                    InAppDeliveryService(InMemoryAlertEventRepository()),
                )
            }
            val response = client.get("/api/v1/notifications/alerts")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }
})
