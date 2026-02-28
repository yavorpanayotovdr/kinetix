package com.kinetix.risk.routes

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.SnapshotType
import com.kinetix.risk.model.SodBaseline
import com.kinetix.risk.model.SodBaselineStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import java.time.Instant
import java.time.LocalDate

class SodSnapshotMapperTest : FunSpec({

    test("SodBaselineStatus.toResponse maps existing baseline correctly") {
        val status = SodBaselineStatus(
            exists = true,
            baselineDate = "2025-01-15",
            snapshotType = SnapshotType.MANUAL,
            createdAt = Instant.parse("2025-01-15T08:00:00Z"),
        )

        val response = status.toResponse()

        response.exists shouldBe true
        response.baselineDate shouldBe "2025-01-15"
        response.snapshotType shouldBe "MANUAL"
        response.createdAt shouldBe "2025-01-15T08:00:00Z"
    }

    test("SodBaselineStatus.toResponse maps missing baseline with nulls") {
        val status = SodBaselineStatus(exists = false)

        val response = status.toResponse()

        response.exists shouldBe false
        response.baselineDate.shouldBeNull()
        response.snapshotType.shouldBeNull()
        response.createdAt.shouldBeNull()
    }

    test("SodBaseline.toSnapshotResponse maps all fields") {
        val baseline = SodBaseline(
            id = 1,
            portfolioId = PortfolioId("port-1"),
            baselineDate = LocalDate.of(2025, 1, 15),
            snapshotType = SnapshotType.AUTO,
            createdAt = Instant.parse("2025-01-15T08:00:00Z"),
        )

        val response = baseline.toSnapshotResponse(snapshotCount = 5)

        response.portfolioId shouldBe "port-1"
        response.baselineDate shouldBe "2025-01-15"
        response.snapshotType shouldBe "AUTO"
        response.createdAt shouldBe "2025-01-15T08:00:00Z"
        response.snapshotCount shouldBe 5
    }
})
