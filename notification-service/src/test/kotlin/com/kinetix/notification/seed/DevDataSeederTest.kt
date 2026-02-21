package com.kinetix.notification.seed

import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.model.AlertType
import com.kinetix.notification.model.Severity
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DevDataSeederTest : FunSpec({

    test("seeds 3 rules when engine is empty") {
        val engine = RulesEngine()
        val seeder = DevDataSeeder(engine)

        seeder.seed()

        engine.listRules() shouldHaveSize 3
    }

    test("skips seeding when rules already exist") {
        val engine = RulesEngine()
        val seeder = DevDataSeeder(engine)

        seeder.seed()
        val rulesAfterFirstSeed = engine.listRules().size

        seeder.seed()

        engine.listRules() shouldHaveSize rulesAfterFirstSeed
    }

    test("seeds VaR breach rule with CRITICAL severity") {
        val engine = RulesEngine()
        DevDataSeeder(engine).seed()

        val varRule = engine.listRules().find { it.type == AlertType.VAR_BREACH }!!
        varRule.threshold shouldBe 1_000_000.0
        varRule.severity shouldBe Severity.CRITICAL
    }

    test("seeds PnL threshold rule with WARNING severity") {
        val engine = RulesEngine()
        DevDataSeeder(engine).seed()

        val pnlRule = engine.listRules().find { it.type == AlertType.PNL_THRESHOLD }!!
        pnlRule.threshold shouldBe 500_000.0
        pnlRule.severity shouldBe Severity.WARNING
    }

    test("seeds risk limit rule with INFO severity") {
        val engine = RulesEngine()
        DevDataSeeder(engine).seed()

        val riskRule = engine.listRules().find { it.type == AlertType.RISK_LIMIT }!!
        riskRule.threshold shouldBe 2_000_000.0
        riskRule.severity shouldBe Severity.INFO
    }
})
