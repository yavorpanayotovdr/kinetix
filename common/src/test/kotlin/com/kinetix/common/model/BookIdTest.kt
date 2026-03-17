package com.kinetix.common.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow

class BookIdTest : FunSpec({

    test("create BookId with valid value") {
        val id = BookId("book-001")
        id.value shouldBe "book-001"
    }

    test("blank BookId throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { BookId("") }
        shouldThrow<IllegalArgumentException> { BookId("   ") }
    }

    test("equal BookIds are equal") {
        BookId("book-001") shouldBe BookId("book-001")
    }

    test("different BookIds are not equal") {
        BookId("book-001") shouldNotBe BookId("book-002")
    }
})
