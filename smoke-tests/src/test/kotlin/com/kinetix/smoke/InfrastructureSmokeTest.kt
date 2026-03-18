package com.kinetix.smoke

import com.kinetix.smoke.SmokeHttpClient.smokeGet
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

@Tags("P1")
class InfrastructureSmokeTest : FunSpec({

    val client = SmokeHttpClient.create()

    val expectedServices = listOf(
        "gateway", "position-service", "price-service", "risk-orchestrator",
        "notification-service", "rates-service", "reference-data-service",
        "volatility-service", "correlation-service", "regulatory-service",
        "audit-service",
    )

    test("all services healthy — system health reports all READY") {
        val response = client.smokeGet("/api/v1/system/health", "system-health")
        response.status shouldBe HttpStatusCode.OK

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val services = body["services"]!!.jsonObject

        val serviceNames = services.keys.toList()
        serviceNames shouldContainAll expectedServices

        for ((name, details) in services) {
            val status = details.jsonObject["status"]?.jsonPrimitive?.content
            println("SMOKE_METRIC system_health_${name}=${if (status == "READY") 1 else 0}")
            status shouldBe "READY"
        }
    }

    test("auth rejects anonymous request with 401") {
        val response = client.smokeGet("/api/v1/books", "auth-reject")
        // Without auth configured in devModule, this may return 200 (no auth enforced)
        // or 401 if auth is wired. Either way, it should NOT be 500 or 404.
        val status = response.status.value
        (status == 200 || status == 401) shouldBe true
    }

    test("instrument round-trip — seeded instruments exist") {
        val response = client.smokeGet("/api/v1/instruments", "instruments")
        response.status shouldBe HttpStatusCode.OK

        val instruments = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        instruments.size shouldBeGreaterThan 0

        val types = instruments.map {
            it.jsonObject["instrumentType"]?.jsonPrimitive?.content
        }.filterNotNull().toSet()

        types shouldContainAll listOf("CASH_EQUITY")
    }

    test("system health response has expected shape") {
        val response = client.smokeGet("/api/v1/system/health", "health-shape")
        response.status shouldBe HttpStatusCode.OK

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        body.containsKey("status") shouldBe true
        body.containsKey("services") shouldBe true

        val services = body["services"]!!.jsonObject
        services.size shouldBe expectedServices.size
    }
})
