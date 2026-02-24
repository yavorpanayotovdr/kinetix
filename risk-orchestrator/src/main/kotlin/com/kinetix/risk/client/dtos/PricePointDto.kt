package com.kinetix.risk.client.dtos

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

@Serializable
data class PricePointDto(
    val instrumentId: String,
    val price: MoneyDto,
    val timestamp: String,
    val source: String,
) {
    fun toDomain(): PricePoint = PricePoint(
        instrumentId = InstrumentId(instrumentId),
        price = price.toDomain(),
        timestamp = Instant.parse(timestamp),
        source = PriceSource.valueOf(source),
    )
}

@Serializable
data class MoneyDto(
    val amount: String,
    val currency: String,
) {
    fun toDomain(): Money = Money(
        amount = BigDecimal(amount),
        currency = java.util.Currency.getInstance(currency),
    )
}
