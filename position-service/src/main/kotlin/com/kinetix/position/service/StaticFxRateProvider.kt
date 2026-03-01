package com.kinetix.position.service

import java.math.BigDecimal
import java.util.Currency

class StaticFxRateProvider(
    private val rates: Map<Pair<Currency, Currency>, BigDecimal>,
) : FxRateProvider {

    override suspend fun getRate(from: Currency, to: Currency): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        return rates[from to to]
    }
}
