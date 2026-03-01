package com.kinetix.position.service

import com.kinetix.position.model.LimitCheckStatus
import com.kinetix.position.model.LimitDefinition
import com.kinetix.position.model.LimitLevel
import com.kinetix.position.model.LimitType
import com.kinetix.position.model.TemporaryLimitIncrease
import com.kinetix.position.persistence.LimitDefinitionRepository
import com.kinetix.position.persistence.TemporaryLimitIncreaseRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class LimitHierarchyServiceTest : FunSpec({

    val limitDefinitionRepo = mockk<LimitDefinitionRepository>()
    val temporaryLimitIncreaseRepo = mockk<TemporaryLimitIncreaseRepository>()
    val service = LimitHierarchyService(limitDefinitionRepo, temporaryLimitIncreaseRepo)

    beforeEach {
        clearMocks(limitDefinitionRepo, temporaryLimitIncreaseRepo)
        coEvery { temporaryLimitIncreaseRepo.findActiveByLimitId(any(), any()) } returns null
        coEvery { limitDefinitionRepo.findByEntityAndType(any(), any(), any()) } returns null
    }

    test("firm limits apply to all desks") {
        val firmLimit = LimitDefinition(
            id = UUID.randomUUID().toString(),
            level = LimitLevel.FIRM,
            entityId = "FIRM",
            limitType = LimitType.NOTIONAL,
            limitValue = BigDecimal("10000000"),
            intradayLimit = null,
            overnightLimit = null,
            active = true,
        )

        coEvery { limitDefinitionRepo.findByEntityAndType("desk-A", LimitLevel.DESK, LimitType.NOTIONAL) } returns null
        coEvery { limitDefinitionRepo.findByEntityAndType("FIRM", LimitLevel.FIRM, LimitType.NOTIONAL) } returns firmLimit

        val result = service.checkLimit(
            entityId = "desk-A",
            level = LimitLevel.DESK,
            limitType = LimitType.NOTIONAL,
            currentExposure = BigDecimal("12000000"),
        )

        result.status shouldBe LimitCheckStatus.BREACHED
        result.limitValue shouldBe BigDecimal("10000000")
    }

    test("desk limits are more restrictive than firm limits") {
        val deskLimit = LimitDefinition(
            id = UUID.randomUUID().toString(),
            level = LimitLevel.DESK,
            entityId = "desk-A",
            limitType = LimitType.NOTIONAL,
            limitValue = BigDecimal("5000000"),
            intradayLimit = null,
            overnightLimit = null,
            active = true,
        )
        val firmLimit = LimitDefinition(
            id = UUID.randomUUID().toString(),
            level = LimitLevel.FIRM,
            entityId = "FIRM",
            limitType = LimitType.NOTIONAL,
            limitValue = BigDecimal("10000000"),
            intradayLimit = null,
            overnightLimit = null,
            active = true,
        )

        coEvery { limitDefinitionRepo.findByEntityAndType("desk-A", LimitLevel.DESK, LimitType.NOTIONAL) } returns deskLimit
        coEvery { limitDefinitionRepo.findByEntityAndType("FIRM", LimitLevel.FIRM, LimitType.NOTIONAL) } returns firmLimit

        val result = service.checkLimit(
            entityId = "desk-A",
            level = LimitLevel.DESK,
            limitType = LimitType.NOTIONAL,
            currentExposure = BigDecimal("6000000"),
        )

        result.status shouldBe LimitCheckStatus.BREACHED
        result.breachedAt shouldBe LimitLevel.DESK
    }

    test("intraday limit is separate from overnight limit") {
        val limit = LimitDefinition(
            id = UUID.randomUUID().toString(),
            level = LimitLevel.TRADER,
            entityId = "trader-1",
            limitType = LimitType.POSITION,
            limitValue = BigDecimal("1000"),
            intradayLimit = BigDecimal("1500"),
            overnightLimit = BigDecimal("800"),
            active = true,
        )

        coEvery { limitDefinitionRepo.findByEntityAndType("trader-1", LimitLevel.TRADER, LimitType.POSITION) } returns limit

        val intradayResult = service.checkLimit(
            entityId = "trader-1",
            level = LimitLevel.TRADER,
            limitType = LimitType.POSITION,
            currentExposure = BigDecimal("1100"),
            intraday = true,
        )

        intradayResult.status shouldBe LimitCheckStatus.OK

        val overnightResult = service.checkLimit(
            entityId = "trader-1",
            level = LimitLevel.TRADER,
            limitType = LimitType.POSITION,
            currentExposure = BigDecimal("900"),
            intraday = false,
        )

        overnightResult.status shouldBe LimitCheckStatus.BREACHED
        overnightResult.limitValue shouldBe BigDecimal("800")
    }

    test("temporary increase overrides base limit until expiry") {
        val limitId = UUID.randomUUID().toString()
        val limit = LimitDefinition(
            id = limitId,
            level = LimitLevel.DESK,
            entityId = "desk-A",
            limitType = LimitType.NOTIONAL,
            limitValue = BigDecimal("5000000"),
            intradayLimit = null,
            overnightLimit = null,
            active = true,
        )
        val tempIncrease = TemporaryLimitIncrease(
            id = UUID.randomUUID().toString(),
            limitId = limitId,
            newValue = BigDecimal("8000000"),
            approvedBy = "risk-manager",
            expiresAt = Instant.now().plusSeconds(3600),
            reason = "Client event",
            createdAt = Instant.now(),
        )

        coEvery { limitDefinitionRepo.findByEntityAndType("desk-A", LimitLevel.DESK, LimitType.NOTIONAL) } returns limit
        coEvery { temporaryLimitIncreaseRepo.findActiveByLimitId(limitId, any()) } returns tempIncrease

        val result = service.checkLimit(
            entityId = "desk-A",
            level = LimitLevel.DESK,
            limitType = LimitType.NOTIONAL,
            currentExposure = BigDecimal("6000000"),
        )

        result.status shouldBe LimitCheckStatus.OK
        result.effectiveLimit shouldBe BigDecimal("8000000")
    }

    test("limit hierarchy traverses from trader up to firm") {
        val traderLimit = LimitDefinition(
            id = UUID.randomUUID().toString(),
            level = LimitLevel.TRADER,
            entityId = "trader-1",
            limitType = LimitType.VAR,
            limitValue = BigDecimal("500000"),
            intradayLimit = null,
            overnightLimit = null,
            active = true,
        )
        val deskLimit = LimitDefinition(
            id = UUID.randomUUID().toString(),
            level = LimitLevel.DESK,
            entityId = "desk-A",
            limitType = LimitType.VAR,
            limitValue = BigDecimal("2000000"),
            intradayLimit = null,
            overnightLimit = null,
            active = true,
        )
        val firmLimit = LimitDefinition(
            id = UUID.randomUUID().toString(),
            level = LimitLevel.FIRM,
            entityId = "FIRM",
            limitType = LimitType.VAR,
            limitValue = BigDecimal("10000000"),
            intradayLimit = null,
            overnightLimit = null,
            active = true,
        )

        coEvery { limitDefinitionRepo.findByEntityAndType("trader-1", LimitLevel.TRADER, LimitType.VAR) } returns traderLimit
        coEvery { limitDefinitionRepo.findByEntityAndType("desk-A", LimitLevel.DESK, LimitType.VAR) } returns deskLimit
        coEvery { limitDefinitionRepo.findByEntityAndType("FIRM", LimitLevel.FIRM, LimitType.VAR) } returns firmLimit

        // Within trader limit but breaches desk limit
        val result = service.checkLimit(
            entityId = "trader-1",
            level = LimitLevel.TRADER,
            limitType = LimitType.VAR,
            currentExposure = BigDecimal("400000"),
            parentEntityIds = mapOf(LimitLevel.DESK to "desk-A", LimitLevel.FIRM to "FIRM"),
            parentExposures = mapOf(LimitLevel.DESK to BigDecimal("2500000"), LimitLevel.FIRM to BigDecimal("8000000")),
        )

        result.status shouldBe LimitCheckStatus.BREACHED
        result.breachedAt shouldBe LimitLevel.DESK
    }

    test("returns OK when exposure is within all limits") {
        val traderLimit = LimitDefinition(
            id = UUID.randomUUID().toString(),
            level = LimitLevel.TRADER,
            entityId = "trader-1",
            limitType = LimitType.NOTIONAL,
            limitValue = BigDecimal("1000000"),
            intradayLimit = null,
            overnightLimit = null,
            active = true,
        )

        coEvery { limitDefinitionRepo.findByEntityAndType("trader-1", LimitLevel.TRADER, LimitType.NOTIONAL) } returns traderLimit

        val result = service.checkLimit(
            entityId = "trader-1",
            level = LimitLevel.TRADER,
            limitType = LimitType.NOTIONAL,
            currentExposure = BigDecimal("500000"),
        )

        result.status shouldBe LimitCheckStatus.OK
    }

    test("returns WARNING when exposure approaches limit") {
        val limit = LimitDefinition(
            id = UUID.randomUUID().toString(),
            level = LimitLevel.DESK,
            entityId = "desk-A",
            limitType = LimitType.NOTIONAL,
            limitValue = BigDecimal("1000000"),
            intradayLimit = null,
            overnightLimit = null,
            active = true,
        )

        coEvery { limitDefinitionRepo.findByEntityAndType("desk-A", LimitLevel.DESK, LimitType.NOTIONAL) } returns limit

        val result = service.checkLimit(
            entityId = "desk-A",
            level = LimitLevel.DESK,
            limitType = LimitType.NOTIONAL,
            currentExposure = BigDecimal("850000"),
        )

        result.status shouldBe LimitCheckStatus.WARNING
    }
})
