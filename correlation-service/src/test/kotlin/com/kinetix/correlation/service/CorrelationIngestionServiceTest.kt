package com.kinetix.correlation.service

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import com.kinetix.correlation.cache.CorrelationCache
import com.kinetix.correlation.kafka.CorrelationPublisher
import com.kinetix.correlation.persistence.CorrelationMatrixRepository
import io.kotest.core.spec.style.FunSpec
import io.mockk.*
import java.time.Instant

class CorrelationIngestionServiceTest : FunSpec({

    val repo = mockk<CorrelationMatrixRepository>()
    val cache = mockk<CorrelationCache>()
    val publisher = mockk<CorrelationPublisher>()

    val service = CorrelationIngestionService(repo, cache, publisher)

    beforeEach {
        clearMocks(repo, cache, publisher)
    }

    test("saves, caches, and publishes correlation matrix") {
        val matrix = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.65, 0.65, 1.0),
            windowDays = 252,
            asOfDate = Instant.parse("2026-02-24T10:00:00Z"),
            method = EstimationMethod.HISTORICAL,
        )

        coEvery { repo.save(matrix) } just Runs
        coEvery { cache.put(matrix.labels, matrix.windowDays, matrix) } just Runs
        coEvery { publisher.publish(matrix) } just Runs

        service.ingest(matrix)

        coVerifyOrder {
            repo.save(matrix)
            cache.put(matrix.labels, matrix.windowDays, matrix)
            publisher.publish(matrix)
        }
    }
})
