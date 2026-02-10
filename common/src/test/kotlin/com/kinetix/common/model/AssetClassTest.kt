package com.kinetix.common.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class AssetClassTest : FunSpec({

    test("AssetClass has all expected values") {
        AssetClass.entries.map { it.name } shouldContainExactlyInAnyOrder listOf(
            "EQUITY", "FIXED_INCOME", "FX", "COMMODITY", "DERIVATIVE"
        )
    }

    test("AssetClass display names are human-readable") {
        AssetClass.EQUITY.displayName shouldBe "Equity"
        AssetClass.FIXED_INCOME.displayName shouldBe "Fixed Income"
        AssetClass.FX.displayName shouldBe "FX"
        AssetClass.COMMODITY.displayName shouldBe "Commodity"
        AssetClass.DERIVATIVE.displayName shouldBe "Derivative"
    }
})
