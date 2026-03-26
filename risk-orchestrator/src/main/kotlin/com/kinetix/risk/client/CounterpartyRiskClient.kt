package com.kinetix.risk.client

import com.kinetix.risk.model.ExposureAtTenor

data class PFEPositionInput(
    val instrumentId: String,
    val marketValue: Double,
    val assetClass: String,
    val volatility: Double,
    val sector: String,
)

data class PFEResult(
    val counterpartyId: String,
    val nettingSetId: String,
    val grossExposure: Double,
    val netExposure: Double,
    val exposureProfile: List<ExposureAtTenor>,
)

data class CVAResult(
    val counterpartyId: String,
    val cva: Double,
    val isEstimated: Boolean,
    val hazardRate: Double,
    val pd1y: Double,
)

interface CounterpartyRiskClient {
    suspend fun calculatePFE(
        counterpartyId: String,
        nettingSetId: String,
        agreementType: String,
        positions: List<PFEPositionInput>,
        numSimulations: Int = 0,
        seed: Long = 0,
    ): PFEResult

    suspend fun calculateCVA(
        counterpartyId: String,
        exposureProfile: List<ExposureAtTenor>,
        lgd: Double,
        pd1y: Double = 0.0,
        cdsSpreadBps: Double = 0.0,
        rating: String = "",
        sector: String = "",
        riskFreeRate: Double = 0.0,
    ): CVAResult
}
