package com.kinetix.regulatory.seed

import com.kinetix.regulatory.stress.ScenarioCategory
import com.kinetix.regulatory.stress.ScenarioStatus
import com.kinetix.regulatory.stress.StressScenarioRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class StressScenarioSeederTest : FunSpec({

    val repository = mockk<StressScenarioRepository>()

    beforeEach {
        clearMocks(repository)
    }

    test("seeds exactly 15 historical scenarios when repository is empty") {
        val savedScenarios = mutableListOf<com.kinetix.regulatory.stress.StressScenario>()
        coEvery { repository.findAll() } returns emptyList()
        coEvery { repository.save(any()) } coAnswers {
            savedScenarios.add(firstArg())
        }

        val seeder = StressScenarioSeeder(repository)
        seeder.seed()

        savedScenarios shouldHaveSize 15
    }

    test("seeds all expected scenario names") {
        val savedNames = mutableListOf<String>()
        coEvery { repository.findAll() } returns emptyList()
        coEvery { repository.save(any()) } coAnswers {
            savedNames.add(firstArg<com.kinetix.regulatory.stress.StressScenario>().name)
        }

        val seeder = StressScenarioSeeder(repository)
        seeder.seed()

        val expectedNames = listOf(
            "GFC_2008",
            "COVID_2020",
            "TAPER_TANTRUM_2013",
            "EURO_CRISIS_2011",
            "BLACK_MONDAY_1987",
            "LTCM_RUSSIAN_1998",
            "DOTCOM_2000",
            "SEPT_11_2001",
            "CHF_DEPEG_2015",
            "BREXIT_2016",
            "VOLMAGEDDON_2018",
            "OIL_NEGATIVE_2020",
            "SVB_BANKING_2023",
            "RATES_SHOCK_2022",
            "EM_CONTAGION",
        )
        savedNames shouldHaveSize expectedNames.size
        expectedNames.forEach { name -> savedNames shouldContain name }
    }

    test("REGULATORY_MANDATED scenarios include the 8 canonical historical scenarios") {
        val savedScenarios = mutableListOf<com.kinetix.regulatory.stress.StressScenario>()
        coEvery { repository.findAll() } returns emptyList()
        coEvery { repository.save(any()) } coAnswers {
            savedScenarios.add(firstArg())
        }

        val seeder = StressScenarioSeeder(repository)
        seeder.seed()

        val regulatoryMandated = savedScenarios.filter { it.category == ScenarioCategory.REGULATORY_MANDATED }
        val regulatoryNames = regulatoryMandated.map { it.name }

        regulatoryNames shouldContain "GFC_2008"
        regulatoryNames shouldContain "COVID_2020"
        regulatoryNames shouldContain "TAPER_TANTRUM_2013"
        regulatoryNames shouldContain "EURO_CRISIS_2011"
        regulatoryNames shouldContain "BLACK_MONDAY_1987"
        regulatoryNames shouldContain "LTCM_RUSSIAN_1998"
        regulatoryNames shouldContain "DOTCOM_2000"
        regulatoryNames shouldContain "SEPT_11_2001"
    }

    test("remaining 7 scenarios are INTERNAL_APPROVED") {
        val savedScenarios = mutableListOf<com.kinetix.regulatory.stress.StressScenario>()
        coEvery { repository.findAll() } returns emptyList()
        coEvery { repository.save(any()) } coAnswers {
            savedScenarios.add(firstArg())
        }

        val seeder = StressScenarioSeeder(repository)
        seeder.seed()

        val internalApproved = savedScenarios.filter { it.category == ScenarioCategory.INTERNAL_APPROVED }
        internalApproved shouldHaveSize 7
    }

    test("all seeded scenarios have APPROVED status") {
        val savedScenarios = mutableListOf<com.kinetix.regulatory.stress.StressScenario>()
        coEvery { repository.findAll() } returns emptyList()
        coEvery { repository.save(any()) } coAnswers {
            savedScenarios.add(firstArg())
        }

        val seeder = StressScenarioSeeder(repository)
        seeder.seed()

        savedScenarios.forEach { scenario ->
            scenario.status shouldBe ScenarioStatus.APPROVED
        }
    }

    test("skips seeding when scenarios already exist") {
        val existing = listOf(
            com.kinetix.regulatory.stress.StressScenario(
                id = "existing-1",
                name = "GFC_2008",
                description = "Already seeded",
                shocks = "{}",
                status = ScenarioStatus.APPROVED,
                createdBy = "system",
                approvedBy = "system",
                approvedAt = java.time.Instant.now(),
                createdAt = java.time.Instant.now(),
            )
        )
        coEvery { repository.findAll() } returns existing

        val seeder = StressScenarioSeeder(repository)
        seeder.seed()

        // save should never be called
        coVerify(exactly = 0) { repository.save(any()) }
    }
})
