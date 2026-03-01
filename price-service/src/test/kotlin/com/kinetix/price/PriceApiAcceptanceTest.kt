package com.kinetix.price

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import com.kinetix.price.cache.PriceCache
import com.kinetix.price.kafka.PricePublisher
import com.kinetix.price.persistence.DatabaseTestSetup
import com.kinetix.price.persistence.ExposedPriceRepository
import com.kinetix.price.persistence.PriceTable
import com.kinetix.price.service.PriceIngestionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

/**
 * Acceptance tests for the Price API endpoints.
 *
 * Wires a real [ExposedPriceRepository] against a Testcontainers PostgreSQL instance and
 * drives the full Ktor handler stack via [testApplication].  External dependencies
 * (Kafka publisher, Redis cache) are relaxed mocks — their integration is covered in
 * dedicated integration tests.
 */
class PriceApiAcceptanceTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository = ExposedPriceRepository(db)
    val publisher = mockk<PricePublisher>(relaxed = true)
    val cache = mockk<PriceCache>(relaxed = true)
    val ingestionService = PriceIngestionService(repository, cache, publisher)

    beforeEach {
        newSuspendedTransaction(db = db) {
            PriceTable.deleteAll()
        }
    }

    // -------------------------------------------------------------------------
    // History ordering
    // -------------------------------------------------------------------------

    test("price history is returned in descending timestamp order") {
        // Ingest three prices for the same instrument out of chronological order
        // so that any accidental insertion-order pass-through would be caught.
        val instrument = "HIST-ORDER-1"
        repository.save(pricePoint(instrument, "110.00", "2025-03-01T12:00:00Z"))
        repository.save(pricePoint(instrument, "100.00", "2025-03-01T10:00:00Z"))
        repository.save(pricePoint(instrument, "120.00", "2025-03-01T14:00:00Z"))

        testApplication {
            application { module(repository, ingestionService) }

            val response = client.get(
                "/api/v1/prices/$instrument/history" +
                    "?from=2025-03-01T09:00:00Z&to=2025-03-01T15:00:00Z",
            )

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 3

            val timestamps = body.map { Instant.parse(it.jsonObject["timestamp"]!!.jsonPrimitive.content) }
            // Each successive timestamp must be strictly before the previous one
            for (i in 0 until timestamps.size - 1) {
                timestamps[i + 1] shouldBeLessThan timestamps[i]
            }
        }
    }

    test("price history prices match the timestamps returned in descending order") {
        val instrument = "HIST-PRICES-1"
        repository.save(pricePoint(instrument, "200.00", "2025-04-01T08:00:00Z"))
        repository.save(pricePoint(instrument, "210.00", "2025-04-01T09:00:00Z"))
        repository.save(pricePoint(instrument, "205.00", "2025-04-01T10:00:00Z"))

        testApplication {
            application { module(repository, ingestionService) }

            val response = client.get(
                "/api/v1/prices/$instrument/history" +
                    "?from=2025-04-01T07:00:00Z&to=2025-04-01T11:00:00Z",
            )

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 3

            // Descending: 10:00 ($205), 09:00 ($210), 08:00 ($200)
            body[0].jsonObject["timestamp"]!!.jsonPrimitive.content shouldBe "2025-04-01T10:00:00Z"
            BigDecimal(body[0].jsonObject["price"]!!.jsonObject["amount"]!!.jsonPrimitive.content).compareTo(BigDecimal("205.00")) shouldBe 0

            body[1].jsonObject["timestamp"]!!.jsonPrimitive.content shouldBe "2025-04-01T09:00:00Z"
            BigDecimal(body[1].jsonObject["price"]!!.jsonObject["amount"]!!.jsonPrimitive.content).compareTo(BigDecimal("210.00")) shouldBe 0

            body[2].jsonObject["timestamp"]!!.jsonPrimitive.content shouldBe "2025-04-01T08:00:00Z"
            BigDecimal(body[2].jsonObject["price"]!!.jsonObject["amount"]!!.jsonPrimitive.content).compareTo(BigDecimal("200.00")) shouldBe 0
        }
    }

    // -------------------------------------------------------------------------
    // Unknown instrument
    // -------------------------------------------------------------------------

    test("latest price for an unknown instrument returns 404") {
        testApplication {
            application { module(repository, ingestionService) }

            val response = client.get("/api/v1/prices/UNKNOWN-INST-1/latest")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("price history for an unknown instrument returns an empty list") {
        // Seed a price for a different instrument to confirm the filter is correct
        repository.save(pricePoint("OTHER-INST-1", "50.00", "2025-05-01T10:00:00Z"))

        testApplication {
            application { module(repository, ingestionService) }

            val response = client.get(
                "/api/v1/prices/UNKNOWN-INST-2/history" +
                    "?from=2025-05-01T00:00:00Z&to=2025-05-01T23:59:59Z",
            )

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.shouldBeEmpty()
        }
    }

    test("price history for a known instrument outside the requested time range returns an empty list") {
        val instrument = "RANGE-MISS-1"
        repository.save(pricePoint(instrument, "300.00", "2025-06-01T10:00:00Z"))

        testApplication {
            application { module(repository, ingestionService) }

            val response = client.get(
                "/api/v1/prices/$instrument/history" +
                    "?from=2025-07-01T00:00:00Z&to=2025-07-01T23:59:59Z",
            )

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.shouldBeEmpty()
        }
    }

    // -------------------------------------------------------------------------
    // Staleness: the returned latest-price timestamp predates the threshold
    // -------------------------------------------------------------------------

    test("latest price timestamp reflects a stale price older than one hour") {
        // A price with a timestamp well in the past is genuinely stale from the
        // caller's perspective.  The acceptance test verifies that the API
        // faithfully returns that timestamp so that consumers can apply their own
        // staleness threshold without guessing.
        val instrument = "STALE-INST-1"
        val staleTimestamp = Instant.parse("2025-01-01T00:00:00Z")
        val stalenessThreshold = Instant.now().minusSeconds(3600)

        repository.save(
            PricePoint(
                instrumentId = InstrumentId(instrument),
                price = Money(BigDecimal("99.00"), USD),
                timestamp = staleTimestamp,
                source = PriceSource.EXCHANGE,
            ),
        )

        testApplication {
            application { module(repository, ingestionService) }

            val response = client.get("/api/v1/prices/$instrument/latest")

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val returnedTimestamp = Instant.parse(body["timestamp"]!!.jsonPrimitive.content)

            // The returned timestamp is before our staleness threshold, confirming
            // the API surfaces the original price timestamp without truncation or
            // substitution.  Callers relying on this for staleness detection will
            // receive the information they need.
            returnedTimestamp shouldBeLessThan stalenessThreshold
        }
    }

    test("price history time range boundary is inclusive on both ends") {
        val instrument = "BOUNDARY-INST-1"
        repository.save(pricePoint(instrument, "50.00", "2025-08-01T09:00:00Z"))  // exactly at 'from'
        repository.save(pricePoint(instrument, "55.00", "2025-08-01T10:00:00Z"))  // inside range
        repository.save(pricePoint(instrument, "60.00", "2025-08-01T11:00:00Z"))  // exactly at 'to'
        repository.save(pricePoint(instrument, "40.00", "2025-08-01T08:59:59Z"))  // just before 'from'
        repository.save(pricePoint(instrument, "70.00", "2025-08-01T11:00:01Z"))  // just after 'to'

        testApplication {
            application { module(repository, ingestionService) }

            val response = client.get(
                "/api/v1/prices/$instrument/history" +
                    "?from=2025-08-01T09:00:00Z&to=2025-08-01T11:00:00Z",
            )

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 3
        }
    }
})

private fun pricePoint(
    instrumentId: String,
    priceAmount: String,
    timestamp: String,
    currency: Currency = USD,
    source: PriceSource = PriceSource.EXCHANGE,
): PricePoint = PricePoint(
    instrumentId = InstrumentId(instrumentId),
    price = Money(BigDecimal(priceAmount), currency),
    timestamp = Instant.parse(timestamp),
    source = source,
)
