package com.kinetix.common.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class SideTest : FunSpec({

    test("Side has BUY and SELL values") {
        Side.entries.map { it.name } shouldContainExactlyInAnyOrder listOf("BUY", "SELL")
    }

    test("BUY sign is 1") {
        Side.BUY.sign shouldBe 1
    }

    test("SELL sign is -1") {
        Side.SELL.sign shouldBe -1
    }
})
