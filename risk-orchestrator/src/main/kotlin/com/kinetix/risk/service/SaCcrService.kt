package com.kinetix.risk.service

import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PositionServiceClient
import com.kinetix.risk.client.ReferenceDataServiceClient
import com.kinetix.risk.client.SaCcrClient
import com.kinetix.risk.client.SaCcrPositionInput
import com.kinetix.risk.client.SaCcrResult
import com.kinetix.risk.client.dtos.CounterpartyTradeDto
import org.slf4j.LoggerFactory

/**
 * Orchestrates SA-CCR (BCBS 279) regulatory capital calculations.
 *
 * SA-CCR is the REGULATORY capital model — deterministic, formulaic.
 * It is distinct from the Monte Carlo PFE model in CounterpartyRiskOrchestrationService.
 */
class SaCcrService(
    private val referenceDataClient: ReferenceDataServiceClient,
    private val saCcrClient: SaCcrClient,
    private val positionServiceClient: PositionServiceClient? = null,
) {
    private val logger = LoggerFactory.getLogger(SaCcrService::class.java)

    suspend fun calculateSaCcr(
        counterpartyId: String,
        positions: List<SaCcrPositionInput> = emptyList(),
        collateralNet: Double = 0.0,
    ): SaCcrResult {
        when (val resp = referenceDataClient.getCounterparty(counterpartyId)) {
            is ClientResponse.Success -> resp.value
            else -> throw IllegalArgumentException("Counterparty not found: $counterpartyId")
        }

        // Fetch live positions from position-service if no explicit positions are provided
        val effectivePositions = if (positions.isEmpty() && positionServiceClient != null) {
            fetchPositionsForCounterparty(counterpartyId)
        } else {
            positions
        }

        val nettingSetId = "$counterpartyId-SA-CCR"

        return saCcrClient.calculateSaCcr(
            nettingSetId = nettingSetId,
            counterpartyId = counterpartyId,
            positions = effectivePositions,
            collateralNet = collateralNet,
        )
    }

    private suspend fun fetchPositionsForCounterparty(counterpartyId: String): List<SaCcrPositionInput> {
        val client = positionServiceClient ?: return emptyList()
        return when (val resp = client.getTradesByCounterparty(counterpartyId)) {
            is ClientResponse.Success -> resp.value.map { it.toSaCcrInput() }
            is ClientResponse.NotFound -> {
                logger.warn("No trades found for counterparty {} in position-service", counterpartyId)
                emptyList()
            }
        }
    }
}

private fun CounterpartyTradeDto.toSaCcrInput() = SaCcrPositionInput(
    instrumentId = instrumentId,
    assetClass = assetClass,
    marketValue = priceAmount.toDouble() * quantity.toDouble() * if (side == "SELL") -1.0 else 1.0,
    notional = priceAmount.toDouble() * quantity.toDouble(),
    currency = priceCurrency,
    payReceive = if (side == "BUY") "PAY_FIXED" else "RECEIVE_FIXED",
    maturityDate = "",
    isOption = false,
    spotPrice = priceAmount.toDouble(),
    strike = 0.0,
    impliedVol = 0.0,
    expiryDays = 0,
    optionType = "",
    quantity = quantity.toDouble(),
)
