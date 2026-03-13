package com.kinetix.risk.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RunLabelTest : FunSpec({

    test("RunLabel values match expected names") {
        RunLabel.entries.map { it.name } shouldBe listOf("ADHOC", "INTRADAY", "OVERNIGHT", "OFFICIAL_EOD")
    }

    test("RunLabel round-trips through valueOf") {
        RunLabel.entries.forEach { label ->
            RunLabel.valueOf(label.name) shouldBe label
        }
    }

    test("OFFICIAL_EOD is distinct from other labels") {
        val eod = RunLabel.OFFICIAL_EOD
        RunLabel.entries.filter { it != eod }.forEach { other ->
            (eod == other) shouldBe false
        }
    }
})
