package com.kinetix.correlation.seed

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import com.kinetix.correlation.persistence.CorrelationMatrixRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot

class DevDataSeederTest : FunSpec({

    val correlationMatrixRepository = mockk<CorrelationMatrixRepository>()
    val seeder = DevDataSeeder(correlationMatrixRepository)

    beforeEach {
        clearMocks(correlationMatrixRepository)
    }

    test("seeds correlation matrix when database is empty") {
        coEvery {
            correlationMatrixRepository.findLatest(listOf("AAPL", "MSFT"), DevDataSeeder.WINDOW_DAYS)
        } returns null
        coEvery { correlationMatrixRepository.save(any()) } just runs

        seeder.seed()

        coVerify(exactly = 1) { correlationMatrixRepository.save(any()) }
    }

    test("skips seeding when data already exists") {
        coEvery {
            correlationMatrixRepository.findLatest(listOf("AAPL", "MSFT"), DevDataSeeder.WINDOW_DAYS)
        } returns CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.82, 0.82, 1.0),
            windowDays = DevDataSeeder.WINDOW_DAYS,
            asOfDate = DevDataSeeder.AS_OF,
            method = EstimationMethod.HISTORICAL,
        )

        seeder.seed()

        coVerify(exactly = 0) { correlationMatrixRepository.save(any()) }
    }

    test("correlation matrix has correct dimensions and properties") {
        coEvery {
            correlationMatrixRepository.findLatest(listOf("AAPL", "MSFT"), DevDataSeeder.WINDOW_DAYS)
        } returns null
        val savedMatrix = slot<CorrelationMatrix>()
        coEvery { correlationMatrixRepository.save(capture(savedMatrix)) } just runs

        seeder.seed()

        val matrix = savedMatrix.captured
        val n = matrix.labels.size
        matrix.values.size shouldBe n * n
        matrix.windowDays shouldBe 252
        matrix.method shouldBe EstimationMethod.HISTORICAL

        // Diagonal should be 1.0
        for (i in 0 until n) {
            matrix.values[i * n + i] shouldBe 1.0
        }

        // Should be symmetric
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                matrix.values[i * n + j] shouldBe matrix.values[j * n + i]
            }
        }
    }

    test("labels are sorted alphabetically") {
        val labels = DevDataSeeder.LABELS
        labels shouldBe labels.sorted()
    }

    test("matrix covers all 22 instruments") {
        DevDataSeeder.LABELS.size shouldBe 22
    }
})
