package com.kinetix.referencedata.routes

import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId
import com.kinetix.referencedata.module
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DeskRepository
import com.kinetix.referencedata.persistence.DivisionRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.service.DeskService
import com.kinetix.referencedata.service.DivisionService
import com.kinetix.referencedata.service.ReferenceDataIngestionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeskRoutesAcceptanceTest : FunSpec({

    val dividendYieldRepo = mockk<DividendYieldRepository>()
    val creditSpreadRepo = mockk<CreditSpreadRepository>()
    val ingestionService = mockk<ReferenceDataIngestionService>()
    val divisionRepo = mockk<DivisionRepository>()
    val deskRepo = mockk<DeskRepository>()
    val divisionService = DivisionService(divisionRepo)
    val deskService = DeskService(deskRepo, divisionRepo)

    val equitiesDivision = Division(id = DivisionId("equities"), name = "Equities")

    test("GET /api/v1/desks returns list of desks") {
        val desks = listOf(
            Desk(id = DeskId("equity-growth"), name = "Equity Growth", divisionId = DivisionId("equities")),
            Desk(id = DeskId("tech-momentum"), name = "Tech Momentum", divisionId = DivisionId("equities"), deskHead = "Alice"),
        )
        coEvery { deskRepo.findAll() } returns desks

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.get("/api/v1/desks")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
            body[0].jsonObject["id"]?.jsonPrimitive?.content shouldBe "equity-growth"
            body[0].jsonObject["divisionId"]?.jsonPrimitive?.content shouldBe "equities"
            body[0].jsonObject["bookCount"]?.jsonPrimitive?.content?.toInt() shouldBe 0
        }
    }

    test("GET /api/v1/desks/{id} returns desk by ID") {
        val desk = Desk(
            id = DeskId("equity-growth"),
            name = "Equity Growth",
            divisionId = DivisionId("equities"),
            deskHead = "Alice",
            description = "Growth equity strategies",
        )
        coEvery { deskRepo.findById(DeskId("equity-growth")) } returns desk

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.get("/api/v1/desks/equity-growth")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["id"]?.jsonPrimitive?.content shouldBe "equity-growth"
            body["name"]?.jsonPrimitive?.content shouldBe "Equity Growth"
            body["divisionId"]?.jsonPrimitive?.content shouldBe "equities"
            body["deskHead"]?.jsonPrimitive?.content shouldBe "Alice"
            body["description"]?.jsonPrimitive?.content shouldBe "Growth equity strategies"
        }
    }

    test("GET /api/v1/desks/{id} returns 404 when not found") {
        coEvery { deskRepo.findById(DeskId("unknown")) } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.get("/api/v1/desks/unknown")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/desks creates a desk and returns 201") {
        coEvery { divisionRepo.findById(DivisionId("equities")) } returns equitiesDivision
        coEvery { deskRepo.findByDivisionId(DivisionId("equities")) } returns emptyList()
        coEvery { deskRepo.save(any()) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.post("/api/v1/desks") {
                contentType(ContentType.Application.Json)
                setBody("""{"id":"equity-growth","name":"Equity Growth","divisionId":"equities","deskHead":"Alice"}""")
            }
            response.status shouldBe HttpStatusCode.Created

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["id"]?.jsonPrimitive?.content shouldBe "equity-growth"
            body["name"]?.jsonPrimitive?.content shouldBe "Equity Growth"
            body["deskHead"]?.jsonPrimitive?.content shouldBe "Alice"
        }
    }

    test("POST /api/v1/desks returns 400 when division does not exist") {
        coEvery { divisionRepo.findById(DivisionId("nonexistent")) } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.post("/api/v1/desks") {
                contentType(ContentType.Application.Json)
                setBody("""{"id":"desk-1","name":"Desk One","divisionId":"nonexistent"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/desks returns 400 when desk name already exists in same division") {
        val existingDesk = Desk(id = DeskId("equity-growth"), name = "Equity Growth", divisionId = DivisionId("equities"))
        coEvery { divisionRepo.findById(DivisionId("equities")) } returns equitiesDivision
        coEvery { deskRepo.findByDivisionId(DivisionId("equities")) } returns listOf(existingDesk)

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.post("/api/v1/desks") {
                contentType(ContentType.Application.Json)
                setBody("""{"id":"equity-growth-2","name":"Equity Growth","divisionId":"equities"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
