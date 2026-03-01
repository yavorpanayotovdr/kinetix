package com.kinetix.position.model

import com.kinetix.common.model.Money
import java.math.BigDecimal
import java.util.Currency

data class CurrencyExposure(
    val currency: Currency,
    val localValue: Money,
    val baseValue: Money,
    val fxRate: BigDecimal,
)
