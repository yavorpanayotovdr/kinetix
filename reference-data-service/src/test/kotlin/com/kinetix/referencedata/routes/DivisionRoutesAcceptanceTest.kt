package com.kinetix.referencedata.routes

import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId
import com.kinetix.referencedata.module
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DeskRepository
import com.kinetix.referencedata.persistence.DivisionRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.persistence.InstrumentRepository
import com.kinetix.referencedata.service.DeskService
import com.kinetix.referencedata.service.DivisionService
import com.kinetix.referencedata.service.InstrumentService
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

class DivisionRoutesAcceptanceTest : FunSpec({

    val dividendYieldRepo = mockk<DividendYieldRepository>()
    val creditSpreadRepo = mockk<CreditSpreadRepository>()
    val ingestionService = mockk<ReferenceDataIngestionService>()
    val divisionRepo = mockk<DivisionRepository>()
    val deskRepo = mockk<DeskRepository>()
    val divisionService = DivisionService(divisionRepo)
    val deskService = DeskService(deskRepo, divisionRepo)

    test("GET /api/v1/divisions returns list of divisions") {
        val divisions = listOf(
            Division(id = DivisionId("equities"), name = "Equities"),
            Division(id = DivisionId("fixed-income"), name = "Fixed Income & Rates"),
        )
        coEvery { divisionRepo.findAll() } returns divisions
        coEvery { deskRepo.findByDivisionId(DivisionId("equities")) } returns emptyList()
        coEvery { deskRepo.findByDivisionId(DivisionId("fixed-income")) } returns emptyList()

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.get("/api/v1/divisions")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
            body[0].jsonObject["id"]?.jsonPrimitive?.content shouldBe "equities"
            body[0].jsonObject["name"]?.jsonPrimitive?.content shouldBe "Equities"
            body[0].jsonObject["deskCount"]?.jsonPrimitive?.content?.toInt() shouldBe 0
        }
    }

    test("GET /api/v1/divisions/{id} returns division by ID") {
        val division = Division(id = DivisionId("equities"), name = "Equities", description = "Equity trading desks")
        coEvery { divisionRepo.findById(DivisionId("equities")) } returns division
        coEvery { deskRepo.findByDivisionId(DivisionId("equities")) } returns emptyList()

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.get("/api/v1/divisions/equities")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["id"]?.jsonPrimitive?.content shouldBe "equities"
            body["name"]?.jsonPrimitive?.content shouldBe "Equities"
            body["description"]?.jsonPrimitive?.content shouldBe "Equity trading desks"
            body["deskCount"]?.jsonPrimitive?.content?.toInt() shouldBe 0
        }
    }

    test("GET /api/v1/divisions/{id} returns 404 when not found") {
        coEvery { divisionRepo.findById(DivisionId("unknown")) } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.get("/api/v1/divisions/unknown")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/divisions creates a division and returns 201") {
        coEvery { divisionRepo.findByName("Equities") } returns null
        coEvery { divisionRepo.save(any()) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.post("/api/v1/divisions") {
                contentType(ContentType.Application.Json)
                setBody("""{"id":"equities","name":"Equities","description":"Equity trading desks"}""")
            }
            response.status shouldBe HttpStatusCode.Created

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["id"]?.jsonPrimitive?.content shouldBe "equities"
            body["name"]?.jsonPrimitive?.content shouldBe "Equities"
        }
    }

    test("POST /api/v1/divisions returns 400 when name already exists") {
        val existing = Division(id = DivisionId("equities-2"), name = "Equities")
        coEvery { divisionRepo.findByName("Equities") } returns existing

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.post("/api/v1/divisions") {
                contentType(ContentType.Application.Json)
                setBody("""{"id":"equities-new","name":"Equities"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/divisions/{divisionId}/desks returns desks in division") {
        coEvery { deskRepo.findByDivisionId(DivisionId("equities")) } returns emptyList()

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, divisionService = divisionService, deskService = deskService) }

            val response = client.get("/api/v1/divisions/equities/desks")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 0
        }
    }
})
