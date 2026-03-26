package com.kinetix.risk.client

import com.kinetix.proto.risk.CalculateCVARequest
import com.kinetix.proto.risk.CalculatePFERequest
import com.kinetix.proto.risk.CounterpartyRiskServiceGrpcKt.CounterpartyRiskServiceCoroutineStub
import com.kinetix.proto.risk.ExposureProfile
import com.kinetix.proto.risk.PFEPositionInput as ProtoPFEPositionInput
import com.kinetix.risk.model.ExposureAtTenor
import java.util.concurrent.TimeUnit

class GrpcCounterpartyRiskClient(
    private val stub: CounterpartyRiskServiceCoroutineStub,
    private val deadlineMs: Long = 120_000,
) : CounterpartyRiskClient {

    override suspend fun calculatePFE(
        counterpartyId: String,
        nettingSetId: String,
        agreementType: String,
        positions: List<PFEPositionInput>,
        numSimulations: Int,
        seed: Long,
    ): PFEResult {
        val request = CalculatePFERequest.newBuilder()
            .setCounterpartyId(counterpartyId)
            .setNettingSetId(nettingSetId)
            .setAgreementType(agreementType)
            .addAllPositions(positions.map { it.toProto() })
            .setNumSimulations(numSimulations)
            .setSeed(seed)
            .build()

        val response = stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .calculatePFE(request)

        return PFEResult(
            counterpartyId = response.counterpartyId,
            nettingSetId = response.nettingSetId,
            grossExposure = response.grossExposure,
            netExposure = response.netExposure,
            exposureProfile = response.exposureProfileList.map { it.toDomain() },
        )
    }

    override suspend fun calculateCVA(
        counterpartyId: String,
        exposureProfile: List<ExposureAtTenor>,
        lgd: Double,
        pd1y: Double,
        cdsSpreadBps: Double,
        rating: String,
        sector: String,
        riskFreeRate: Double,
    ): CVAResult {
        val request = CalculateCVARequest.newBuilder()
            .setCounterpartyId(counterpartyId)
            .addAllExposureProfile(exposureProfile.map { it.toProto() })
            .setLgd(lgd)
            .setPd1Y(pd1y)
            .setCdsSpreadBps(cdsSpreadBps)
            .setRating(rating)
            .setSector(sector)
            .setRiskFreeRate(riskFreeRate)
            .build()

        val response = stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .calculateCVA(request)

        return CVAResult(
            counterpartyId = response.counterpartyId,
            cva = response.cva,
            isEstimated = response.isEstimated,
            hazardRate = response.hazardRate,
            pd1y = response.pd1Y,
        )
    }
}

private fun PFEPositionInput.toProto(): ProtoPFEPositionInput =
    ProtoPFEPositionInput.newBuilder()
        .setInstrumentId(instrumentId)
        .setMarketValue(marketValue)
        .setAssetClass(assetClass)
        .setVolatility(volatility)
        .setSector(sector)
        .build()

private fun ExposureProfile.toDomain() = ExposureAtTenor(
    tenor = tenor,
    tenorYears = tenorYears,
    expectedExposure = expectedExposure,
    pfe95 = pfe95,
    pfe99 = pfe99,
)

private fun ExposureAtTenor.toProto(): ExposureProfile =
    ExposureProfile.newBuilder()
        .setTenor(tenor)
        .setTenorYears(tenorYears)
        .setExpectedExposure(expectedExposure)
        .setPfe95(pfe95)
        .setPfe99(pfe99)
        .build()
