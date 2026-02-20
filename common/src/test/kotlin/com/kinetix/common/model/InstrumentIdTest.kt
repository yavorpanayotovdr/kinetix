package com.kinetix.common.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class InstrumentIdTest : FunSpec({

    test("create InstrumentId with valid value") {
        val id = InstrumentId("AAPL")
        id.value shouldBe "AAPL"
    }

    test("blank InstrumentId throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { InstrumentId("") }
        shouldThrow<IllegalArgumentException> { InstrumentId("   ") }
    }

    test("equal InstrumentIds are equal") {
        InstrumentId("AAPL") shouldBe InstrumentId("AAPL")
    }

    test("different InstrumentIds are not equal") {
        InstrumentId("AAPL") shouldNotBe InstrumentId("MSFT")
    }
})
