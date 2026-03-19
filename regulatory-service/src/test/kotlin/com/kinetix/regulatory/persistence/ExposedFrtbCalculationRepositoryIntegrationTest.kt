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
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val NOW = Instant.now()

private fun record(
    id: String = UUID.randomUUID().toString(),
    bookId: String = "port-1",
    totalSbmCharge: BigDecimal = BigDecimal("1000.0"),
    grossJtd: BigDecimal = BigDecimal("500.0"),
    hedgeBenefit: BigDecimal = BigDecimal("100.0"),
    netDrc: BigDecimal = BigDecimal("400.0"),
    exoticNotional: BigDecimal = BigDecimal("200.0"),
    otherNotional: BigDecimal = BigDecimal("150.0"),
    totalRrao: BigDecimal = BigDecimal("50.0"),
    totalCapitalCharge: BigDecimal = BigDecimal("1450.0"),
    sbmCharges: List<RiskClassCharge> = listOf(
        RiskClassCharge("GIRR", BigDecimal("300.0"), BigDecimal("200.0"), BigDecimal("100.0"), BigDecimal("600.0")),
        RiskClassCharge("CSR", BigDecimal("150.0"), BigDecimal("100.0"), BigDecimal("50.0"), BigDecimal("300.0")),
    ),
    calculatedAt: Instant = NOW,
    storedAt: Instant = NOW,
) = FrtbCalculationRecord(
    id = id,
    bookId = bookId,
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

    test("save and findLatestByBookId returns the record") {
        repository.save(record())

        val found = repository.findLatestByBookId("port-1")
        found.shouldNotBeNull()
        found.bookId shouldBe "port-1"
        found.totalSbmCharge shouldBe BigDecimal("1000.00000000")
        found.totalCapitalCharge shouldBe BigDecimal("1450.00000000")
        found.sbmCharges shouldHaveSize 2
        found.sbmCharges[0].riskClass shouldBe "GIRR"
        found.sbmCharges[0].totalCharge shouldBe BigDecimal.valueOf(600.0)
    }

    test("findLatestByBookId returns null for unknown portfolio") {
        repository.findLatestByBookId("unknown").shouldBeNull()
    }

    test("findLatestByBookId returns the most recent calculation") {
        repository.save(record(id = "r1", calculatedAt = NOW.minus(10, ChronoUnit.DAYS), totalCapitalCharge = BigDecimal("1000.0")))
        repository.save(record(id = "r2", calculatedAt = NOW.minus(5, ChronoUnit.DAYS), totalCapitalCharge = BigDecimal("1500.0")))

        val found = repository.findLatestByBookId("port-1")
        found.shouldNotBeNull()
        found.totalCapitalCharge shouldBe BigDecimal("1500.00000000")
    }

    test("findByBookId returns records with limit and offset") {
        repository.save(record(id = "r1", calculatedAt = NOW.minus(10, ChronoUnit.DAYS)))
        repository.save(record(id = "r2", calculatedAt = NOW.minus(5, ChronoUnit.DAYS)))
        repository.save(record(id = "r3", calculatedAt = NOW))

        val page1 = repository.findByBookId("port-1", limit = 2, offset = 0)
        page1 shouldHaveSize 2

        val page2 = repository.findByBookId("port-1", limit = 2, offset = 2)
        page2 shouldHaveSize 1
    }

    test("findByBookId returns empty list for unknown portfolio") {
        repository.findByBookId("unknown", limit = 10, offset = 0) shouldHaveSize 0
    }

    test("save preserves JSON serialized sbmCharges") {
        val charges = listOf(
            RiskClassCharge("GIRR", BigDecimal("100.0"), BigDecimal("200.0"), BigDecimal("50.0"), BigDecimal("350.0")),
            RiskClassCharge("CSR", BigDecimal("75.0"), BigDecimal("125.0"), BigDecimal("25.0"), BigDecimal("225.0")),
            RiskClassCharge("Equity", BigDecimal("50.0"), BigDecimal("80.0"), BigDecimal("30.0"), BigDecimal("160.0")),
        )
        repository.save(record(sbmCharges = charges))

        val found = repository.findLatestByBookId("port-1")
        found.shouldNotBeNull()
        found.sbmCharges shouldHaveSize 3
        found.sbmCharges[2].riskClass shouldBe "Equity"
        found.sbmCharges[2].deltaCharge shouldBe BigDecimal.valueOf(50.0)
    }
})
