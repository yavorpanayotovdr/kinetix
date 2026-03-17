package com.kinetix.referencedata.seed

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.Desk
import com.kinetix.common.model.Division
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DeskRepository
import com.kinetix.referencedata.persistence.DivisionRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

class DevDataSeederTest : FunSpec({

    val dividendYieldRepository = mockk<DividendYieldRepository>()
    val creditSpreadRepository = mockk<CreditSpreadRepository>()
    val divisionRepository = mockk<DivisionRepository>()
    val deskRepository = mockk<DeskRepository>()
    val seeder = DevDataSeeder(dividendYieldRepository, creditSpreadRepository, divisionRepository = divisionRepository, deskRepository = deskRepository)

    beforeEach {
        clearMocks(dividendYieldRepository, creditSpreadRepository, divisionRepository, deskRepository)
    }

    test("seeds dividend yields and credit spreads when database is empty") {
        coEvery { dividendYieldRepository.findLatest(InstrumentId("AAPL")) } returns null
        coEvery { dividendYieldRepository.save(any()) } just runs
        coEvery { creditSpreadRepository.save(any()) } just runs
        coEvery { divisionRepository.save(any()) } just runs
        coEvery { deskRepository.save(any()) } just runs

        seeder.seed()

        coVerify(exactly = 9) { dividendYieldRepository.save(any()) }
        coVerify(exactly = 6) { creditSpreadRepository.save(any()) }
    }

    test("seeds all divisions when database is empty") {
        coEvery { dividendYieldRepository.findLatest(InstrumentId("AAPL")) } returns null
        coEvery { dividendYieldRepository.save(any()) } just runs
        coEvery { creditSpreadRepository.save(any()) } just runs
        val savedDivisions = mutableListOf<Division>()
        coEvery { divisionRepository.save(capture(savedDivisions)) } just runs
        coEvery { deskRepository.save(any()) } just runs

        seeder.seed()

        coVerify(exactly = 3) { divisionRepository.save(any()) }
        savedDivisions.map { it.id.value } shouldBe listOf("equities", "fixed-income-rates", "multi-asset")
    }

    test("seeds all desks when database is empty") {
        coEvery { dividendYieldRepository.findLatest(InstrumentId("AAPL")) } returns null
        coEvery { dividendYieldRepository.save(any()) } just runs
        coEvery { creditSpreadRepository.save(any()) } just runs
        coEvery { divisionRepository.save(any()) } just runs
        val savedDesks = mutableListOf<Desk>()
        coEvery { deskRepository.save(capture(savedDesks)) } just runs

        seeder.seed()

        coVerify(exactly = 8) { deskRepository.save(any()) }
        savedDesks.filter { it.divisionId.value == "equities" }.size shouldBe 3
        savedDesks.filter { it.divisionId.value == "fixed-income-rates" }.size shouldBe 1
        savedDesks.filter { it.divisionId.value == "multi-asset" }.size shouldBe 4
    }

    test("skips seeding when data already exists") {
        coEvery { dividendYieldRepository.findLatest(InstrumentId("AAPL")) } returns DividendYield(
            instrumentId = InstrumentId("AAPL"),
            yield = 0.0055,
            exDate = null,
            asOfDate = DevDataSeeder.AS_OF,
            source = ReferenceDataSource.BLOOMBERG,
        )

        seeder.seed()

        coVerify(exactly = 0) { dividendYieldRepository.save(any()) }
        coVerify(exactly = 0) { creditSpreadRepository.save(any()) }
        coVerify(exactly = 0) { divisionRepository.save(any()) }
        coVerify(exactly = 0) { deskRepository.save(any()) }
    }

    test("dividend yields use BLOOMBERG source") {
        coEvery { dividendYieldRepository.findLatest(InstrumentId("AAPL")) } returns null
        val savedYields = mutableListOf<DividendYield>()
        coEvery { dividendYieldRepository.save(capture(savedYields)) } just runs
        coEvery { creditSpreadRepository.save(any()) } just runs
        coEvery { divisionRepository.save(any()) } just runs
        coEvery { deskRepository.save(any()) } just runs

        seeder.seed()

        savedYields.forEach { it.source shouldBe ReferenceDataSource.BLOOMBERG }
    }

    test("credit spreads use RATING_AGENCY source and have ratings") {
        coEvery { dividendYieldRepository.findLatest(InstrumentId("AAPL")) } returns null
        coEvery { dividendYieldRepository.save(any()) } just runs
        val savedSpreads = mutableListOf<CreditSpread>()
        coEvery { creditSpreadRepository.save(capture(savedSpreads)) } just runs
        coEvery { divisionRepository.save(any()) } just runs
        coEvery { deskRepository.save(any()) } just runs

        seeder.seed()

        savedSpreads.forEach {
            it.source shouldBe ReferenceDataSource.RATING_AGENCY
            (it.rating != null) shouldBe true
        }

        val jpm = savedSpreads.first { it.instrumentId.value == "JPM" }
        jpm.rating shouldBe "A+"
    }
})
