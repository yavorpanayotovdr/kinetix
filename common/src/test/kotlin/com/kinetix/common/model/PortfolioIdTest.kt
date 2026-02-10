package com.kinetix.common.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow

class PortfolioIdTest : FunSpec({

    test("create PortfolioId with valid value") {
        val id = PortfolioId("port-001")
        id.value shouldBe "port-001"
    }

    test("blank PortfolioId throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { PortfolioId("") }
        shouldThrow<IllegalArgumentException> { PortfolioId("   ") }
    }

    test("equal PortfolioIds are equal") {
        PortfolioId("port-001") shouldBe PortfolioId("port-001")
    }

    test("different PortfolioIds are not equal") {
        PortfolioId("port-001") shouldNotBe PortfolioId("port-002")
    }
})
