package com.kinetix.position.service

import com.kinetix.position.model.CollateralBalance
import com.kinetix.position.model.CollateralDirection
import com.kinetix.position.model.CollateralType
import com.kinetix.position.persistence.CollateralBalanceRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Manages collateral balances for counterparty credit risk.
 *
 * Applies collateral type haircuts as per spec:
 *   CASH            0%
 *   GOVERNMENT_BOND 3%
 *   CORPORATE_BOND  10%
 *   EQUITY          20%
 *
 * net_net_exposure = net_exposure - collateral_received + collateral_posted
 */
class CollateralTrackingService(
    private val repository: CollateralBalanceRepository,
) {
    suspend fun postCollateral(
        counterpartyId: String,
        nettingSetId: String?,
        collateralType: CollateralType,
        amount: BigDecimal,
        currency: String,
        direction: CollateralDirection,
        asOfDate: LocalDate = LocalDate.now(),
    ): CollateralBalance {
        require(amount > BigDecimal.ZERO) { "Collateral amount must be positive" }

        val valueAfterHaircut = amount * (BigDecimal.ONE - collateralType.haircut)

        val balance = CollateralBalance(
            counterpartyId = counterpartyId,
            nettingSetId = nettingSetId,
            collateralType = collateralType,
            amount = amount,
            currency = currency,
            direction = direction,
            asOfDate = asOfDate,
            valueAfterHaircut = valueAfterHaircut,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        return repository.save(balance)
    }

    suspend fun getCollateralForCounterparty(counterpartyId: String): List<CollateralBalance> =
        repository.findByCounterpartyId(counterpartyId)

    suspend fun getCollateralForNettingSet(nettingSetId: String): List<CollateralBalance> =
        repository.findByNettingSetId(nettingSetId)

    /**
     * Compute net collateral effect for a counterparty.
     * Returns (collateral_received, collateral_posted) — both positive values.
     * net_net_exposure = net_exposure - received + posted
     */
    suspend fun netCollateral(counterpartyId: String): Pair<BigDecimal, BigDecimal> {
        val balances = repository.findByCounterpartyId(counterpartyId)
        val received = balances
            .filter { it.direction == CollateralDirection.RECEIVED }
            .fold(BigDecimal.ZERO) { acc, b -> acc + b.valueAfterHaircut }
        val posted = balances
            .filter { it.direction == CollateralDirection.POSTED }
            .fold(BigDecimal.ZERO) { acc, b -> acc + b.valueAfterHaircut }
        return Pair(received, posted)
    }
}
