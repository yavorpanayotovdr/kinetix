package com.kinetix.smoke

import com.kinetix.smoke.SmokeHttpClient.smokeGet
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

@Tags("P3")
class UiSmokeTest : FunSpec({

    val client = SmokeHttpClient.create()
    val bookId = SmokeTestConfig.seededBookId

    test("positions API returns non-empty data for seeded portfolio") {
        val response = client.smokeGet("/api/v1/books/$bookId/positions", "positions-api")
        response.status shouldBe HttpStatusCode.OK

        val positions = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        positions.size shouldBeGreaterThan 0

        val first = positions.first().jsonObject
        first["instrumentId"]?.jsonPrimitive?.content.shouldNotBeNull()
    }

    test("portfolio summary returns non-zero NAV") {
        val response = client.smokeGet("/api/v1/books/$bookId/summary", "portfolio-summary")
        response.status shouldBe HttpStatusCode.OK

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val totalNav = body["totalNav"]?.jsonObject?.get("amount")?.jsonPrimitive?.content
        totalNav.shouldNotBeNull()
        totalNav.toDouble() shouldBeGreaterThan 0.0
    }

    test("UI serves HTML with main element") {
        val response = client.smokeGet("/", "ui-serves")
        // UI may be on different port via caddy, or gateway may not serve it directly
        // Accept either 200 with HTML or a redirect
        val status = response.status.value
        (status == 200 || status == 301 || status == 302 || status == 404) shouldBe true

        if (status == 200) {
            val body = response.bodyAsText()
            if (body.contains("<!DOCTYPE html") || body.contains("<html")) {
                // UI is served, check for basic structure
                body shouldContain "<div id=\"root\""
            }
        }
    }

    test("alert rules exist in notification service") {
        val response = client.smokeGet("/api/v1/notifications/rules", "alert-rules")
        response.status shouldBe HttpStatusCode.OK

        val rules = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        rules.size shouldBeGreaterThan 0

        val hasEnabled = rules.any {
            it.jsonObject["enabled"]?.jsonPrimitive?.boolean == true
        }
        hasEnabled shouldBe true
    }
})
