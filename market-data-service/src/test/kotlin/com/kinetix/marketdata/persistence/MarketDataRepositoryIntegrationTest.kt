package com.kinetix.marketdata.persistence

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import com.kinetix.common.model.MarketDataSource
import com.kinetix.common.model.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private fun point(
    instrumentId: String = "AAPL",
    priceAmount: BigDecimal = BigDecimal("150.00"),
    currency: Currency = USD,
    timestamp: Instant = Instant.parse("2025-01-15T10:00:00Z"),
    source: MarketDataSource = MarketDataSource.EXCHANGE,
) = MarketDataPoint(
    instrumentId = InstrumentId(instrumentId),
    price = Money(priceAmount, currency),
    timestamp = timestamp,
    source = source,
)

class MarketDataRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: MarketDataRepository = ExposedMarketDataRepository()

    beforeEach {
        newSuspendedTransaction { MarketDataTable.deleteAll() }
    }

    test("save and retrieve latest market data point") {
        val p = point()
        repository.save(p)

        val found = repository.findLatest(InstrumentId("AAPL"))
        found.shouldNotBeNull()
        found.instrumentId shouldBe InstrumentId("AAPL")
        found.price.amount.compareTo(BigDecimal("150.00")) shouldBe 0
        found.price.currency shouldBe USD
        found.timestamp shouldBe Instant.parse("2025-01-15T10:00:00Z")
        found.source shouldBe MarketDataSource.EXCHANGE
    }

    test("findLatest returns null for unknown instrument") {
        repository.findLatest(InstrumentId("UNKNOWN")).shouldBeNull()
    }

    test("findLatest returns most recent data point") {
        repository.save(point(timestamp = Instant.parse("2025-01-15T10:00:00Z"), priceAmount = BigDecimal("150.00")))
        repository.save(point(timestamp = Instant.parse("2025-01-15T11:00:00Z"), priceAmount = BigDecimal("155.00")))
        repository.save(point(timestamp = Instant.parse("2025-01-15T09:00:00Z"), priceAmount = BigDecimal("148.00")))

        val found = repository.findLatest(InstrumentId("AAPL"))
        found.shouldNotBeNull()
        found.price.amount.compareTo(BigDecimal("155.00")) shouldBe 0
        found.timestamp shouldBe Instant.parse("2025-01-15T11:00:00Z")
    }

    test("findByInstrumentId returns points in time range ordered by timestamp") {
        repository.save(point(timestamp = Instant.parse("2025-01-15T08:00:00Z"), priceAmount = BigDecimal("145.00")))
        repository.save(point(timestamp = Instant.parse("2025-01-15T10:00:00Z"), priceAmount = BigDecimal("150.00")))
        repository.save(point(timestamp = Instant.parse("2025-01-15T12:00:00Z"), priceAmount = BigDecimal("155.00")))
        repository.save(point(timestamp = Instant.parse("2025-01-15T14:00:00Z"), priceAmount = BigDecimal("160.00")))

        val results = repository.findByInstrumentId(
            InstrumentId("AAPL"),
            from = Instant.parse("2025-01-15T09:00:00Z"),
            to = Instant.parse("2025-01-15T13:00:00Z"),
        )
        results shouldHaveSize 2
        results[0].price.amount.compareTo(BigDecimal("150.00")) shouldBe 0
        results[1].price.amount.compareTo(BigDecimal("155.00")) shouldBe 0
    }

    test("findByInstrumentId returns empty list for no matches") {
        repository.save(point(instrumentId = "AAPL"))

        val results = repository.findByInstrumentId(
            InstrumentId("MSFT"),
            from = Instant.parse("2025-01-15T00:00:00Z"),
            to = Instant.parse("2025-01-15T23:59:59Z"),
        )
        results shouldHaveSize 0
    }

    test("save preserves BigDecimal precision") {
        val p = point(priceAmount = BigDecimal("98765.432109876543"))
        repository.save(p)

        val found = repository.findLatest(InstrumentId("AAPL"))
        found.shouldNotBeNull()
        found.price.amount.compareTo(BigDecimal("98765.432109876543")) shouldBe 0
    }

    test("save and retrieve with all market data sources") {
        MarketDataSource.entries.forEachIndexed { idx, src ->
            repository.save(
                point(
                    instrumentId = "INST-$idx",
                    timestamp = Instant.parse("2025-01-15T10:00:00Z"),
                    source = src,
                )
            )
            val found = repository.findLatest(InstrumentId("INST-$idx"))
            found.shouldNotBeNull()
            found.source shouldBe src
        }
    }
})
