package com.kinetix.referencedata.service

import com.kinetix.referencedata.model.InstrumentLiquidityTier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InstrumentLiquidityServiceTest : FunSpec({

    context("classifyTier") {
        test("TIER_1 when adv is above 50 million and spread is below 5 bps") {
            InstrumentLiquidityService.classifyTier(adv = 75_000_000.0, bidAskSpreadBps = 3.0) shouldBe InstrumentLiquidityTier.TIER_1
        }

        test("TIER_1 boundary: adv exactly 50 million and spread exactly 5 bps") {
            InstrumentLiquidityService.classifyTier(adv = 50_000_000.0, bidAskSpreadBps = 5.0) shouldBe InstrumentLiquidityTier.TIER_1
        }

        test("not TIER_1 when adv is above 50 million but spread is above 5 bps") {
            InstrumentLiquidityService.classifyTier(adv = 75_000_000.0, bidAskSpreadBps = 6.0) shouldBe InstrumentLiquidityTier.TIER_2
        }

        test("not TIER_1 when spread is below 5 bps but adv is below 50 million") {
            InstrumentLiquidityService.classifyTier(adv = 49_000_000.0, bidAskSpreadBps = 2.0) shouldBe InstrumentLiquidityTier.TIER_2
        }

        test("TIER_2 when adv is above 10 million and spread is below 20 bps") {
            InstrumentLiquidityService.classifyTier(adv = 25_000_000.0, bidAskSpreadBps = 10.0) shouldBe InstrumentLiquidityTier.TIER_2
        }

        test("TIER_2 boundary: adv exactly 10 million and spread exactly 20 bps") {
            InstrumentLiquidityService.classifyTier(adv = 10_000_000.0, bidAskSpreadBps = 20.0) shouldBe InstrumentLiquidityTier.TIER_2
        }

        test("not TIER_2 when adv is above 10 million but spread is above 20 bps") {
            InstrumentLiquidityService.classifyTier(adv = 25_000_000.0, bidAskSpreadBps = 21.0) shouldBe InstrumentLiquidityTier.TIER_3
        }

        test("TIER_3 when adv is above 1 million regardless of spread") {
            InstrumentLiquidityService.classifyTier(adv = 5_000_000.0, bidAskSpreadBps = 50.0) shouldBe InstrumentLiquidityTier.TIER_3
        }

        test("TIER_3 boundary: adv exactly 1 million") {
            InstrumentLiquidityService.classifyTier(adv = 1_000_000.0, bidAskSpreadBps = 100.0) shouldBe InstrumentLiquidityTier.TIER_3
        }

        test("ILLIQUID when adv is below 1 million") {
            InstrumentLiquidityService.classifyTier(adv = 500_000.0, bidAskSpreadBps = 5.0) shouldBe InstrumentLiquidityTier.ILLIQUID
        }

        test("ILLIQUID when adv is zero") {
            InstrumentLiquidityService.classifyTier(adv = 0.0, bidAskSpreadBps = 0.0) shouldBe InstrumentLiquidityTier.ILLIQUID
        }
    }
})
