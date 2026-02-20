package com.kinetix.common.model

import java.math.BigDecimal
import java.util.Currency

data class Money(val amount: BigDecimal, val currency: Currency) {

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add $currency and ${other.currency}" }
        return Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract $currency and ${other.currency}" }
        return Money(amount - other.amount, currency)
    }

    operator fun times(scalar: BigDecimal): Money = Money(amount * scalar, currency)

    operator fun unaryMinus(): Money = Money(-amount, currency)

    companion object {
        fun zero(currency: Currency): Money = Money(BigDecimal.ZERO, currency)
    }
}
