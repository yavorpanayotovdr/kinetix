package com.kinetix.risk.client

import com.google.protobuf.Timestamp
import com.kinetix.common.model.LiquidityTier
import com.kinetix.proto.common.AssetClass as ProtoAssetClass
import com.kinetix.proto.common.BookId as ProtoBookId
import com.kinetix.proto.risk.LiquidityAdjustedVaRResponse
import com.kinetix.proto.risk.LiquidityRiskServiceGrpcKt.LiquidityRiskServiceCoroutineStub
import com.kinetix.proto.risk.LiquidityTier as ProtoLiquidityTier
import com.kinetix.proto.risk.PositionLiquidityRisk as ProtoPositionLiquidityRisk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

class GrpcLiquidityClientTest : FunSpec({

    val stub = mockk<LiquidityRiskServiceCoroutineStub>()
    val deadlinedStub = mockk<LiquidityRiskServiceCoroutineStub>()
    val client = GrpcLiquidityClient(stub)

    beforeTest {
        clearMocks(stub, deadlinedStub)
        every { stub.withDeadlineAfter(any(), any()) } returns deadlinedStub
    }

    fun sampleResponse(bookId: String = "BOOK-1") = LiquidityAdjustedVaRResponse.newBuilder()
        .setBookId(ProtoBookId.newBuilder().setValue(bookId))
        .setPortfolioLvar(316_227.0)
        .setDataCompleteness(1.0)
        .setPortfolioConcentrationStatus("OK")
        .setCalculatedAt(Timestamp.newBuilder().setSeconds(1_700_000_000L))
        .addPositionRisks(
            ProtoPositionLiquidityRisk.newBuilder()
                .setInstrumentId("AAPL")
                .setAssetClass(ProtoAssetClass.EQUITY)
                .setMarketValue(500_000.0)
                .setTier(ProtoLiquidityTier.HIGH_LIQUID)
                .setHorizonDays(1)
                .setAdv(10_000_000.0)
                .setAdvMissing(false)
                .setAdvStale(false)
                .setLvarContribution(316_227.0)
                .setConcentrationStatus("OK")
        )
        .build()

    test("calculateLiquidityAdjustedVaR returns domain result for successful response") {
        coEvery { deadlinedStub.calculateLiquidityAdjustedVaR(any(), any()) } returns sampleResponse()

        val result = client.calculateLiquidityAdjustedVaR(
            bookId = "BOOK-1",
            baseVar = 100_000.0,
            baseHoldingPeriod = 1,
            liquidityInputs = emptyList(),
        )

        result.bookId shouldBe "BOOK-1"
        result.portfolioLvar shouldBe 316_227.0
        result.dataCompleteness shouldBe 1.0
        result.portfolioConcentrationStatus shouldBe "OK"
        result.positionRisks.size shouldBe 1
    }

    test("maps position tier from proto to domain LiquidityTier") {
        coEvery { deadlinedStub.calculateLiquidityAdjustedVaR(any(), any()) } returns sampleResponse()

        val result = client.calculateLiquidityAdjustedVaR("BOOK-1", 100_000.0, 1, emptyList())

        result.positionRisks.first().tier shouldBe LiquidityTier.HIGH_LIQUID
    }

    test("maps ILLIQUID tier correctly") {
        val illiquidResponse = LiquidityAdjustedVaRResponse.newBuilder()
            .setBookId(ProtoBookId.newBuilder().setValue("BOOK-1"))
            .setPortfolioLvar(316_227.0)
            .setDataCompleteness(0.0)
            .setPortfolioConcentrationStatus("BREACHED")
            .setCalculatedAt(Timestamp.newBuilder().setSeconds(1_700_000_000L))
            .addPositionRisks(
                ProtoPositionLiquidityRisk.newBuilder()
                    .setInstrumentId("NO-ADV")
                    .setAssetClass(ProtoAssetClass.EQUITY)
                    .setMarketValue(1_000_000.0)
                    .setTier(ProtoLiquidityTier.ILLIQUID)
                    .setHorizonDays(10)
                    .setAdv(0.0)
                    .setAdvMissing(true)
                    .setAdvStale(false)
                    .setLvarContribution(316_227.0)
                    .setConcentrationStatus("BREACHED")
            )
            .build()

        coEvery { deadlinedStub.calculateLiquidityAdjustedVaR(any(), any()) } returns illiquidResponse

        val result = client.calculateLiquidityAdjustedVaR("BOOK-1", 100_000.0, 1, emptyList())

        result.positionRisks.first().tier shouldBe LiquidityTier.ILLIQUID
        result.positionRisks.first().advMissing shouldBe true
        result.portfolioConcentrationStatus shouldBe "BREACHED"
    }
})
