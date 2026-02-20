package com.kinetix.common.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TradeIdTest : FunSpec({

    test("create TradeId with valid value") {
        val id = TradeId("trade-001")
        id.value shouldBe "trade-001"
    }

    test("blank TradeId throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { TradeId("") }
        shouldThrow<IllegalArgumentException> { TradeId("   ") }
    }

    test("equal TradeIds are equal") {
        TradeId("trade-001") shouldBe TradeId("trade-001")
    }

    test("different TradeIds are not equal") {
        TradeId("trade-001") shouldNotBe TradeId("trade-002")
    }
})
