package com.kinetix.price.feed

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import java.math.BigDecimal
import java.util.Currency

data class InstrumentSeed(
    val instrumentId: InstrumentId,
    val initialPrice: BigDecimal,
    val currency: Currency,
    val assetClass: AssetClass = AssetClass.EQUITY,
)
