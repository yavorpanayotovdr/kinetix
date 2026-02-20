package com.kinetix.common.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigDecimal
import java.util.Currency

class MoneyTest : FunSpec({

    test("create Money with valid amount and currency") {
        val money = Money(BigDecimal("100.50"), Currency.getInstance("USD"))
        money.amount shouldBe BigDecimal("100.50")
        money.currency shouldBe Currency.getInstance("USD")
    }

    test("add two Money values with same currency") {
        val a = Money(BigDecimal("100.00"), Currency.getInstance("USD"))
        val b = Money(BigDecimal("50.25"), Currency.getInstance("USD"))
        val result = a + b
        result.amount shouldBe BigDecimal("150.25")
        result.currency shouldBe Currency.getInstance("USD")
    }

    test("subtract two Money values with same currency") {
        val a = Money(BigDecimal("100.00"), Currency.getInstance("USD"))
        val b = Money(BigDecimal("30.50"), Currency.getInstance("USD"))
        val result = a - b
        result.amount shouldBe BigDecimal("69.50")
        result.currency shouldBe Currency.getInstance("USD")
    }

    test("adding different currencies throws IllegalArgumentException") {
        val usd = Money(BigDecimal("100.00"), Currency.getInstance("USD"))
        val eur = Money(BigDecimal("50.00"), Currency.getInstance("EUR"))
        shouldThrow<IllegalArgumentException> { usd + eur }
    }

    test("multiply Money by scalar") {
        val money = Money(BigDecimal("100.00"), Currency.getInstance("USD"))
        val result = money * BigDecimal("3")
        result.amount shouldBe BigDecimal("300.00")
        result.currency shouldBe Currency.getInstance("USD")
    }

    test("negate Money") {
        val money = Money(BigDecimal("100.00"), Currency.getInstance("USD"))
        val negated = -money
        negated.amount shouldBe BigDecimal("-100.00")
    }

    test("Money.ZERO creates zero amount for given currency") {
        val zero = Money.zero(Currency.getInstance("GBP"))
        zero.amount shouldBe BigDecimal.ZERO
        zero.currency shouldBe Currency.getInstance("GBP")
    }
})
