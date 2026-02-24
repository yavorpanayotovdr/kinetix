package com.kinetix.referencedata.service

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import com.kinetix.referencedata.cache.ReferenceDataCache
import com.kinetix.referencedata.kafka.ReferenceDataPublisher
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import io.kotest.core.spec.style.FunSpec
import io.mockk.*
import java.time.Instant
import java.time.LocalDate

private val NOW = Instant.parse("2026-02-24T10:00:00Z")

class ReferenceDataIngestionServiceTest : FunSpec({

    val dividendYieldRepo = mockk<DividendYieldRepository>()
    val creditSpreadRepo = mockk<CreditSpreadRepository>()
    val cache = mockk<ReferenceDataCache>()
    val publisher = mockk<ReferenceDataPublisher>()

    val service = ReferenceDataIngestionService(dividendYieldRepo, creditSpreadRepo, cache, publisher)

    beforeEach {
        clearMocks(dividendYieldRepo, creditSpreadRepo, cache, publisher)
    }

    test("saves, caches, and publishes dividend yield") {
        val dividendYield = DividendYield(
            instrumentId = InstrumentId("AAPL"),
            yield = 0.0065,
            exDate = LocalDate.of(2026, 3, 15),
            asOfDate = NOW,
            source = ReferenceDataSource.BLOOMBERG,
        )

        coEvery { dividendYieldRepo.save(dividendYield) } just Runs
        coEvery { cache.putDividendYield(dividendYield) } just Runs
        coEvery { publisher.publishDividendYield(dividendYield) } just Runs

        service.ingest(dividendYield)

        coVerify(ordering = Ordering.ORDERED) {
            dividendYieldRepo.save(dividendYield)
            cache.putDividendYield(dividendYield)
            publisher.publishDividendYield(dividendYield)
        }
    }

    test("saves, caches, and publishes credit spread") {
        val creditSpread = CreditSpread(
            instrumentId = InstrumentId("CORP-BOND-1"),
            spread = 0.0125,
            rating = "AA",
            asOfDate = NOW,
            source = ReferenceDataSource.RATING_AGENCY,
        )

        coEvery { creditSpreadRepo.save(creditSpread) } just Runs
        coEvery { cache.putCreditSpread(creditSpread) } just Runs
        coEvery { publisher.publishCreditSpread(creditSpread) } just Runs

        service.ingest(creditSpread)

        coVerify(ordering = Ordering.ORDERED) {
            creditSpreadRepo.save(creditSpread)
            cache.putCreditSpread(creditSpread)
            publisher.publishCreditSpread(creditSpread)
        }
    }
})
