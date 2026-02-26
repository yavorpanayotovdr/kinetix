package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.FrtbCalculationRecord
import com.kinetix.regulatory.model.RiskClassCharge
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun record(
    id: String = UUID.randomUUID().toString(),
    portfolioId: String = "port-1",
    totalSbmCharge: Double = 1000.0,
    grossJtd: Double = 500.0,
    hedgeBenefit: Double = 100.0,
    netDrc: Double = 400.0,
    exoticNotional: Double = 200.0,
    otherNotional: Double = 150.0,
    totalRrao: Double = 50.0,
    totalCapitalCharge: Double = 1450.0,
    sbmCharges: List<RiskClassCharge> = listOf(
        RiskClassCharge("GIRR", 300.0, 200.0, 100.0, 600.0),
        RiskClassCharge("CSR", 150.0, 100.0, 50.0, 300.0),
    ),
    calculatedAt: Instant = NOW,
    storedAt: Instant = NOW,
) = FrtbCalculationRecord(
    id = id,
    portfolioId = portfolioId,
    totalSbmCharge = totalSbmCharge,
    grossJtd = grossJtd,
    hedgeBenefit = hedgeBenefit,
    netDrc = netDrc,
    exoticNotional = exoticNotional,
    otherNotional = otherNotional,
    totalRrao = totalRrao,
    totalCapitalCharge = totalCapitalCharge,
    sbmCharges = sbmCharges,
    calculatedAt = calculatedAt,
    storedAt = storedAt,
)

class ExposedFrtbCalculationRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: FrtbCalculationRepository = ExposedFrtbCalculationRepository()

    beforeEach {
        newSuspendedTransaction { FrtbCalculationsTable.deleteAll() }
    }

    test("save and findLatestByPortfolioId returns the record") {
        repository.save(record())

        val found = repository.findLatestByPortfolioId("port-1")
        found.shouldNotBeNull()
        found.portfolioId shouldBe "port-1"
        found.totalSbmCharge shouldBe 1000.0
        found.totalCapitalCharge shouldBe 1450.0
        found.sbmCharges shouldHaveSize 2
        found.sbmCharges[0].riskClass shouldBe "GIRR"
        found.sbmCharges[0].totalCharge shouldBe 600.0
    }

    test("findLatestByPortfolioId returns null for unknown portfolio") {
        repository.findLatestByPortfolioId("unknown").shouldBeNull()
    }

    test("findLatestByPortfolioId returns the most recent calculation") {
        repository.save(record(id = "r1", calculatedAt = Instant.parse("2026-01-10T10:00:00Z"), totalCapitalCharge = 1000.0))
        repository.save(record(id = "r2", calculatedAt = Instant.parse("2026-01-15T10:00:00Z"), totalCapitalCharge = 1500.0))

        val found = repository.findLatestByPortfolioId("port-1")
        found.shouldNotBeNull()
        found.totalCapitalCharge shouldBe 1500.0
    }

    test("findByPortfolioId returns records with limit and offset") {
        repository.save(record(id = "r1", calculatedAt = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(record(id = "r2", calculatedAt = Instant.parse("2026-01-15T10:00:00Z")))
        repository.save(record(id = "r3", calculatedAt = Instant.parse("2026-01-20T10:00:00Z")))

        val page1 = repository.findByPortfolioId("port-1", limit = 2, offset = 0)
        page1 shouldHaveSize 2

        val page2 = repository.findByPortfolioId("port-1", limit = 2, offset = 2)
        page2 shouldHaveSize 1
    }

    test("findByPortfolioId returns empty list for unknown portfolio") {
        repository.findByPortfolioId("unknown", limit = 10, offset = 0) shouldHaveSize 0
    }

    test("save preserves JSON serialized sbmCharges") {
        val charges = listOf(
            RiskClassCharge("GIRR", 100.0, 200.0, 50.0, 350.0),
            RiskClassCharge("CSR", 75.0, 125.0, 25.0, 225.0),
            RiskClassCharge("Equity", 50.0, 80.0, 30.0, 160.0),
        )
        repository.save(record(sbmCharges = charges))

        val found = repository.findLatestByPortfolioId("port-1")
        found.shouldNotBeNull()
        found.sbmCharges shouldHaveSize 3
        found.sbmCharges[2].riskClass shouldBe "Equity"
        found.sbmCharges[2].deltaCharge shouldBe 50.0
    }
})
