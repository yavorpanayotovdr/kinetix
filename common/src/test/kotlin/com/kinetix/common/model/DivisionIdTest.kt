package com.kinetix.common.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DivisionIdTest : FunSpec({

    test("create DivisionId with valid value") {
        DivisionId("div-1").value shouldBe "div-1"
    }

    test("blank DivisionId throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { DivisionId("") }
    }

    test("whitespace DivisionId throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { DivisionId("   ") }
    }

    test("equal DivisionIds are equal") {
        DivisionId("div-1") shouldBe DivisionId("div-1")
    }

    test("different DivisionIds are not equal") {
        DivisionId("div-1") shouldNotBe DivisionId("div-2")
    }
})
