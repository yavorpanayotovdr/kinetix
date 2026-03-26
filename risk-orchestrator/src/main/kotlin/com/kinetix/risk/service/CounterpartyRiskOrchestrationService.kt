package com.kinetix.risk.service

import com.kinetix.risk.client.CVAResult
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.CounterpartyRiskClient
import com.kinetix.risk.client.PFEPositionInput
import com.kinetix.risk.client.ReferenceDataServiceClient
import com.kinetix.risk.client.dtos.CounterpartyDto
import com.kinetix.risk.client.dtos.NettingAgreementDto
import com.kinetix.risk.model.CounterpartyExposureSnapshot
import com.kinetix.risk.model.ExposureAtTenor
import com.kinetix.risk.model.NettingSetExposure
import com.kinetix.risk.persistence.CounterpartyExposureRepository
import org.slf4j.LoggerFactory
import java.time.Instant

class CounterpartyRiskOrchestrationService(
    private val referenceDataClient: ReferenceDataServiceClient,
    private val counterpartyRiskClient: CounterpartyRiskClient,
    private val repository: CounterpartyExposureRepository,
) {
    private val logger = LoggerFactory.getLogger(CounterpartyRiskOrchestrationService::class.java)

    /**
     * Fetches netting set data for the counterparty, runs PFE via Monte Carlo for each
     * netting set, and persists the combined snapshot.
     *
     * For v1: assumes positions all belong to the first netting agreement found.
     * If no netting agreement exists, treats all positions as a single un-netted set.
     */
    suspend fun computeAndPersistPFE(
        counterpartyId: String,
        positions: List<PFEPositionInput>,
        numSimulations: Int = 0,
        seed: Long = 0L,
    ): CounterpartyExposureSnapshot {
        val counterparty = when (val resp = referenceDataClient.getCounterparty(counterpartyId)) {
            is ClientResponse.Success -> resp.value
            else -> throw IllegalArgumentException("Counterparty not found: $counterpartyId")
        }

        val nettingAgreements = when (val resp = referenceDataClient.getNettingAgreements(counterpartyId)) {
            is ClientResponse.Success -> resp.value
            else -> emptyList()
        }

        val primaryAgreement = nettingAgreements.firstOrNull()
        val nettingSetId = primaryAgreement?.nettingSetId ?: "$counterpartyId-DEFAULT"
        val agreementType = primaryAgreement?.agreementType ?: "NONE"

        val pfeResult = counterpartyRiskClient.calculatePFE(
            counterpartyId = counterpartyId,
            nettingSetId = nettingSetId,
            agreementType = agreementType,
            positions = positions,
            numSimulations = numSimulations,
            seed = seed,
        )

        val peakPfe = if (pfeResult.exposureProfile.isEmpty()) 0.0
        else pfeResult.exposureProfile.maxOf { it.pfe95 }

        // CVA requires credit data (pd1y or cdsSpreadBps). Without it the result would be
        // a misleading zero rather than a meaningful figure, so we skip the calculation
        // and leave cva null to signal "unknown" rather than "zero risk".
        val cvaResult = if (!hasCreditData(counterparty)) {
            logger.warn(
                "Skipping CVA for counterparty {} — no credit data (pd1y and cdsSpreadBps are both absent)",
                counterpartyId,
            )
            null
        } else {
            try {
                counterpartyRiskClient.calculateCVA(
                    counterpartyId = counterpartyId,
                    exposureProfile = pfeResult.exposureProfile,
                    lgd = counterparty.lgd,
                    pd1y = counterparty.pd1y ?: 0.0,
                    cdsSpreadssBps = counterparty.cdsSpreadBps ?: 0.0,
                    rating = counterparty.ratingSp ?: "",
                    sector = counterparty.sector ?: "",
                    riskFreeRate = 0.0,
                )
            } catch (e: Exception) {
                logger.warn("CVA calculation failed for {}: {}", counterpartyId, e.message)
                null
            }
        }

        // Collateral: use CSA threshold as an approximation of collateral held.
        // collateralHeld = min(csaThreshold, netExposure) — we never hold more than the exposure.
        val csaThreshold = primaryAgreement?.csaThreshold ?: 0.0
        val collateralHeld = minOf(csaThreshold, pfeResult.netExposure).coerceAtLeast(0.0)
        val collateralPosted = 0.0 // v1: assume we post no collateral

        // netNetExposure = netExposure - collateralHeld + collateralPosted (spec invariant)
        val netNetExposure = pfeResult.netExposure - collateralHeld + collateralPosted

        // Wrong-way risk: financial-sector counterparties create wrong-way risk correlation.
        val wrongWayRiskFlags = computeWrongWayRiskFlags(counterparty)

        // Per-netting-set breakdown (v1: single netting set from primary agreement).
        val nettingSetExposures = listOf(
            NettingSetExposure(
                nettingSetId = nettingSetId,
                agreementType = agreementType,
                netExposure = pfeResult.netExposure,
                peakPfe = peakPfe,
            )
        )

        val snapshot = CounterpartyExposureSnapshot(
            counterpartyId = counterpartyId,
            calculatedAt = Instant.now(),
            pfeProfile = pfeResult.exposureProfile,
            currentNetExposure = pfeResult.netExposure,
            peakPfe = peakPfe,
            cva = cvaResult?.cva,
            cvaEstimated = cvaResult?.isEstimated ?: false,
            nettingSetExposures = nettingSetExposures,
            collateralHeld = collateralHeld,
            collateralPosted = collateralPosted,
            netNetExposure = netNetExposure,
            wrongWayRiskFlags = wrongWayRiskFlags,
        )

        return repository.save(snapshot)
    }

    /**
     * Wrong-way risk arises when the counterparty's credit quality deteriorates at the same time
     * as our exposure to them increases.  Financial-sector counterparties are the primary vector:
     * they tend to be stressed precisely when financial markets are dislocated and our exposures peak.
     */
    private fun computeWrongWayRiskFlags(counterparty: CounterpartyDto): List<String> {
        val flags = mutableListOf<String>()
        if (counterparty.isFinancial) {
            flags.add("FINANCIAL_SECTOR_WRONG_WAY_RISK: counterparty sector correlated with market stress")
        }
        val sector = counterparty.sector?.uppercase() ?: ""
        if (sector in setOf("SOVEREIGN", "GOVERNMENT")) {
            flags.add("SOVEREIGN_WRONG_WAY_RISK: sovereign counterparty exposure may spike during crises")
        }
        return flags
    }

    /**
     * Computes CVA for the counterparty using its credit data from reference data service
     * and a pre-computed exposure profile (typically from a PFE run).
     *
     * Throws [IllegalStateException] if the counterparty has no credit data (both pd1y and
     * cdsSpreadBps are absent), because computing CVA without credit data would silently
     * return zero — which looks like "no risk" rather than "risk unknown".
     */
    suspend fun computeCVA(
        counterpartyId: String,
        exposureProfile: List<ExposureAtTenor>,
    ): CVAResult {
        val counterparty = when (val resp = referenceDataClient.getCounterparty(counterpartyId)) {
            is ClientResponse.Success -> resp.value
            else -> throw IllegalArgumentException("Counterparty not found: $counterpartyId")
        }

        if (!hasCreditData(counterparty)) {
            throw IllegalStateException(
                "Counterparty $counterpartyId has no credit data (pd1y and cdsSpreadBps are both absent); CVA cannot be computed"
            )
        }

        return counterpartyRiskClient.calculateCVA(
            counterpartyId = counterpartyId,
            exposureProfile = exposureProfile,
            lgd = counterparty.lgd,
            pd1y = counterparty.pd1y ?: 0.0,
            cdsSpreadssBps = counterparty.cdsSpreadBps ?: 0.0,
            rating = counterparty.ratingSp ?: "",
            sector = counterparty.sector ?: "",
            riskFreeRate = 0.0,
        )
    }

    /**
     * Returns true when the counterparty has at least one source of credit data.
     * CVA is only meaningful when credit data is available; without it the calculation
     * would return zero, which is indistinguishable from "genuinely zero credit risk".
     */
    private fun hasCreditData(counterparty: CounterpartyDto): Boolean =
        counterparty.pd1y != null || counterparty.cdsSpreadBps != null

    suspend fun getLatestExposure(counterpartyId: String): CounterpartyExposureSnapshot? =
        repository.findLatestByCounterpartyId(counterpartyId)

    suspend fun getExposureHistory(counterpartyId: String, limit: Int = 90): List<CounterpartyExposureSnapshot> =
        repository.findByCounterpartyId(counterpartyId, limit)

    suspend fun getAllLatestExposures(): List<CounterpartyExposureSnapshot> =
        repository.findLatestForAllCounterparties()
}
