package com.kinetix.volatility.service

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import com.kinetix.volatility.cache.VolatilityCache
import com.kinetix.volatility.kafka.VolatilityPublisher
import com.kinetix.volatility.persistence.VolSurfaceRepository
import io.kotest.core.spec.style.FunSpec
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant

class VolatilityIngestionServiceTest : FunSpec({

    val repository = mockk<VolSurfaceRepository>()
    val cache = mockk<VolatilityCache>()
    val publisher = mockk<VolatilityPublisher>()

    val service = VolatilityIngestionService(repository, cache, publisher)

    beforeEach {
        clearMocks(repository, cache, publisher)
    }

    test("saves, caches, and publishes volatility surface") {
        val surface = VolSurface(
            instrumentId = InstrumentId("AAPL"),
            asOf = Instant.parse("2026-02-24T10:00:00Z"),
            points = listOf(
                VolPoint(BigDecimal("100"), 30, BigDecimal("0.25")),
                VolPoint(BigDecimal("100"), 90, BigDecimal("0.28")),
            ),
            source = VolatilitySource.BLOOMBERG,
        )

        coEvery { repository.save(surface) } just Runs
        coEvery { cache.putSurface(surface) } just Runs
        coEvery { publisher.publishSurface(surface) } just Runs

        service.ingest(surface)

        coVerify(ordering = Ordering.ORDERED) {
            repository.save(surface)
            cache.putSurface(surface)
            publisher.publishSurface(surface)
        }
    }
})
