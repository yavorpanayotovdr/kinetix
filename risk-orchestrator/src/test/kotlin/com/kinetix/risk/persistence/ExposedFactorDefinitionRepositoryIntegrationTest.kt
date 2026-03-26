package com.kinetix.risk.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ExposedFactorDefinitionRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: FactorDefinitionRepository = ExposedFactorDefinitionRepository(db)

    test("findAll returns all five seeded factor definitions") {
        val definitions = repository.findAll()

        definitions shouldHaveSize 5
        definitions.map { it.factorName } shouldContainExactlyInAnyOrder listOf(
            "EQUITY_BETA",
            "RATES_DURATION",
            "CREDIT_SPREAD",
            "FX_DELTA",
            "VOL_EXPOSURE",
        )
    }

    test("findByName returns the correct factor definition") {
        val definition = repository.findByName("EQUITY_BETA")

        definition shouldNotBe null
        definition!!.factorName shouldBe "EQUITY_BETA"
        definition.proxyInstrumentId shouldBe "IDX-SPX"
        definition.description shouldBe "Equity market beta — proxy: S&P 500"
    }

    test("findByName returns null for unknown factor") {
        val definition = repository.findByName("UNKNOWN_FACTOR")

        definition shouldBe null
    }

    test("each seeded definition has a non-null proxy instrument id") {
        val definitions = repository.findAll()

        definitions.forEach { it.proxyInstrumentId shouldNotBe null }
    }

    test("seeded proxy instrument ids match Python constants") {
        val definitions = repository.findAll().associateBy { it.factorName }

        definitions["EQUITY_BETA"]!!.proxyInstrumentId shouldBe "IDX-SPX"
        definitions["RATES_DURATION"]!!.proxyInstrumentId shouldBe "US10Y"
        definitions["CREDIT_SPREAD"]!!.proxyInstrumentId shouldBe "CDX-IG"
        definitions["FX_DELTA"]!!.proxyInstrumentId shouldBe "EURUSD"
        definitions["VOL_EXPOSURE"]!!.proxyInstrumentId shouldBe "VIX"
    }
})
