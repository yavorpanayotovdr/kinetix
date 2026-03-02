package com.kinetix.position.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val EUR = Currency.getInstance("EUR")
private val GBP = Currency.getInstance("GBP")
private val JPY = Currency.getInstance("JPY")

class LiveFxRateProviderTest : FunSpec({

    val delegate = mockk<FxRateProvider>()

    beforeEach {
        io.mockk.clearMocks(delegate)
        coEvery { delegate.getRate(any(), any()) } returns null
    }

    test("returns ONE when from and to currencies are the same") {
        val provider = LiveFxRateProvider(delegate)

        provider.getRate(USD, USD) shouldBe BigDecimal.ONE
    }

    test("returns live rate for a directly published FX pair") {
        val provider = LiveFxRateProvider(delegate)

        provider.onPriceUpdate("EURUSD", BigDecimal("1.0856"))

        provider.getRate(EUR, USD) shouldBe BigDecimal("1.0856")
    }

    test("derives inverse rate when only the reverse pair is published") {
        val provider = LiveFxRateProvider(delegate)

        provider.onPriceUpdate("EURUSD", BigDecimal("1.0856"))

        val expected = BigDecimal.ONE.divide(BigDecimal("1.0856"), 10, RoundingMode.HALF_UP)
        provider.getRate(USD, EUR) shouldBe expected
    }

    test("falls back to delegate when no live rate is available") {
        coEvery { delegate.getRate(GBP, USD) } returns BigDecimal("1.27")
        val provider = LiveFxRateProvider(delegate)

        provider.getRate(GBP, USD) shouldBe BigDecimal("1.27")
    }

    test("returns null when neither live nor delegate has a rate") {
        val provider = LiveFxRateProvider(delegate)

        provider.getRate(GBP, JPY) shouldBe null
    }

    test("uses the latest rate when multiple updates arrive") {
        val provider = LiveFxRateProvider(delegate)

        provider.onPriceUpdate("EURUSD", BigDecimal("1.0800"))
        provider.onPriceUpdate("EURUSD", BigDecimal("1.0900"))

        provider.getRate(EUR, USD) shouldBe BigDecimal("1.0900")
    }

    test("ignores non-6-char instrument IDs") {
        val provider = LiveFxRateProvider(delegate)

        provider.onPriceUpdate("AAPL", BigDecimal("189.25"))
        provider.onPriceUpdate("SPX-PUT-4500", BigDecimal("28.75"))

        provider.getRate(USD, USD) shouldBe BigDecimal.ONE // identity still works
    }

    test("ignores 6-char IDs with invalid currency codes") {
        val provider = LiveFxRateProvider(delegate)

        provider.onPriceUpdate("ABCXYZ", BigDecimal("1.50"))

        // Should not have stored anything — delegate returns null
        provider.getRate(USD, EUR) shouldBe null
    }

    test("live rate takes priority over delegate") {
        coEvery { delegate.getRate(EUR, USD) } returns BigDecimal("1.08")
        val provider = LiveFxRateProvider(delegate)

        provider.onPriceUpdate("EURUSD", BigDecimal("1.0950"))

        provider.getRate(EUR, USD) shouldBe BigDecimal("1.0950")
    }
})
