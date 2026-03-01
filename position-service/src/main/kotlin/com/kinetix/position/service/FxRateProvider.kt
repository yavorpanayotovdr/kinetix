package com.kinetix.position.service

import java.math.BigDecimal
import java.util.Currency

interface FxRateProvider {
    suspend fun getRate(from: Currency, to: Currency): BigDecimal?
}
