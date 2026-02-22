package com.kinetix.notification.seed

import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.model.AlertType
import com.kinetix.notification.model.Severity
import com.kinetix.notification.persistence.InMemoryAlertEventRepository
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DevDataSeederTest : FunSpec({

    test("seeds 6 rules and 20 alert events when empty") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        val eventRepo = InMemoryAlertEventRepository()
        val seeder = DevDataSeeder(engine, eventRepo)

        seeder.seed()

        engine.listRules() shouldHaveSize 6
        eventRepo.findRecent(50) shouldHaveSize 20
    }

    test("skips seeding when rules already exist") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        val eventRepo = InMemoryAlertEventRepository()
        val seeder = DevDataSeeder(engine, eventRepo)

        seeder.seed()
        val rulesAfterFirstSeed = engine.listRules().size

        seeder.seed()

        engine.listRules() shouldHaveSize rulesAfterFirstSeed
    }

    test("seeds VaR breach rule with CRITICAL severity") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        val eventRepo = InMemoryAlertEventRepository()
        DevDataSeeder(engine, eventRepo).seed()

        val varRule = engine.listRules().find { it.id == "seed-rule-var-breach" }!!
        varRule.threshold shouldBe 1_000_000.0
        varRule.severity shouldBe Severity.CRITICAL
    }

    test("seeds PnL threshold rule with WARNING severity") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        val eventRepo = InMemoryAlertEventRepository()
        DevDataSeeder(engine, eventRepo).seed()

        val pnlRule = engine.listRules().find { it.id == "seed-rule-pnl-threshold" }!!
        pnlRule.threshold shouldBe 500_000.0
        pnlRule.severity shouldBe Severity.WARNING
    }

    test("seeds risk limit rule with INFO severity") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        val eventRepo = InMemoryAlertEventRepository()
        DevDataSeeder(engine, eventRepo).seed()

        val riskRule = engine.listRules().find { it.id == "seed-rule-risk-limit" }!!
        riskRule.threshold shouldBe 2_000_000.0
        riskRule.severity shouldBe Severity.INFO
    }
})
