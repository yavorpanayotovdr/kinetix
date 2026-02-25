package com.kinetix.referencedata.seed

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import com.kinetix.referencedata.persistence.CreditSpreadRepository
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
    val seeder = DevDataSeeder(dividendYieldRepository, creditSpreadRepository)

    beforeEach {
        clearMocks(dividendYieldRepository, creditSpreadRepository)
    }

    test("seeds dividend yields and credit spreads when database is empty") {
        coEvery { dividendYieldRepository.findLatest(InstrumentId("AAPL")) } returns null
        coEvery { dividendYieldRepository.save(any()) } just runs
        coEvery { creditSpreadRepository.save(any()) } just runs

        seeder.seed()

        coVerify(exactly = 9) { dividendYieldRepository.save(any()) }
        coVerify(exactly = 6) { creditSpreadRepository.save(any()) }
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
    }

    test("dividend yields use BLOOMBERG source") {
        coEvery { dividendYieldRepository.findLatest(InstrumentId("AAPL")) } returns null
        val savedYields = mutableListOf<DividendYield>()
        coEvery { dividendYieldRepository.save(capture(savedYields)) } just runs
        coEvery { creditSpreadRepository.save(any()) } just runs

        seeder.seed()

        savedYields.forEach { it.source shouldBe ReferenceDataSource.BLOOMBERG }
    }

    test("credit spreads use RATING_AGENCY source and have ratings") {
        coEvery { dividendYieldRepository.findLatest(InstrumentId("AAPL")) } returns null
        coEvery { dividendYieldRepository.save(any()) } just runs
        val savedSpreads = mutableListOf<CreditSpread>()
        coEvery { creditSpreadRepository.save(capture(savedSpreads)) } just runs

        seeder.seed()

        savedSpreads.forEach {
            it.source shouldBe ReferenceDataSource.RATING_AGENCY
            (it.rating != null) shouldBe true
        }

        val jpm = savedSpreads.first { it.instrumentId.value == "JPM" }
        jpm.rating shouldBe "A+"
    }
})
