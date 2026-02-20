package com.kinetix.position.service

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.position.persistence.PositionRepository
import org.slf4j.LoggerFactory

class PriceUpdateService(
    private val positionRepository: PositionRepository,
) {
    private val logger = LoggerFactory.getLogger(PriceUpdateService::class.java)

    suspend fun handle(instrumentId: InstrumentId, newPrice: Money): Int {
        val positions = positionRepository.findByInstrumentId(instrumentId)
        if (positions.isEmpty()) {
            return 0
        }

        var updatedCount = 0
        for (position in positions) {
            if (position.currency != newPrice.currency) {
                logger.debug(
                    "Skipping position {}/{}: currency mismatch (position={}, price={})",
                    position.portfolioId.value, instrumentId.value,
                    position.currency.currencyCode, newPrice.currency.currencyCode,
                )
                continue
            }
            val updated = position.markToMarket(newPrice)
            positionRepository.save(updated)
            updatedCount++
        }

        logger.info(
            "Updated {} positions for instrument {} to price {} {}",
            updatedCount, instrumentId.value, newPrice.amount.toPlainString(), newPrice.currency.currencyCode,
        )
        return updatedCount
    }
}
