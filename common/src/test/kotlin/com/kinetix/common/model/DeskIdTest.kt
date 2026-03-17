package com.kinetix.common.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DeskIdTest : FunSpec({

    test("create DeskId with valid value") {
        DeskId("desk-1").value shouldBe "desk-1"
    }

    test("blank DeskId throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { DeskId("") }
    }

    test("whitespace DeskId throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { DeskId("   ") }
    }

    test("equal DeskIds are equal") {
        DeskId("desk-1") shouldBe DeskId("desk-1")
    }

    test("different DeskIds are not equal") {
        DeskId("desk-1") shouldNotBe DeskId("desk-2")
    }
})
