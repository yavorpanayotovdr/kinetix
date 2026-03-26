package com.kinetix.risk.service

import java.math.BigDecimal

interface FxRateProvider {
    suspend fun getRate(fromCurrency: String, toCurrency: String): BigDecimal?
}
