package com.kinetix.regulatory.stress

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

class ScenarioCategoryTest : FunSpec({

    val repository = mockk<StressScenarioRepository>()
    val service = StressScenarioService(repository)

    test("scenario category defaults to INTERNAL_APPROVED when not specified") {
        coEvery { repository.save(any()) } returns Unit

        val result = service.create(
            name = "Parametric Scenario",
            description = "Basic equity shock",
            shocks = """{"equity":-0.10}""",
            createdBy = "analyst-1",
        )

        result.category shouldBe ScenarioCategory.INTERNAL_APPROVED
    }

    test("scenario category can be set to REGULATORY_MANDATED at creation") {
        coEvery { repository.save(any()) } returns Unit

        val result = service.create(
            name = "GFC_2008",
            description = "Global Financial Crisis 2008",
            shocks = """{"equity":-0.40}""",
            createdBy = "system",
            category = ScenarioCategory.REGULATORY_MANDATED,
        )

        result.category shouldBe ScenarioCategory.REGULATORY_MANDATED
        coVerify { repository.save(match { it.category == ScenarioCategory.REGULATORY_MANDATED }) }
    }

    test("scenario category can be set to SUPERVISORY_REQUESTED at creation") {
        coEvery { repository.save(any()) } returns Unit

        val result = service.create(
            name = "SUPERVISORY_EQUITY_SHOCK",
            description = "Requested by regulator in Q1 review",
            shocks = """{"equity":-0.25}""",
            createdBy = "regulator-liaison",
            category = ScenarioCategory.SUPERVISORY_REQUESTED,
        )

        result.category shouldBe ScenarioCategory.SUPERVISORY_REQUESTED
    }

    test("category is preserved through approve lifecycle") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenarioWithCategory(
            id = id,
            status = ScenarioStatus.PENDING_APPROVAL,
            category = ScenarioCategory.REGULATORY_MANDATED,
        )
        coEvery { repository.findById(id) } returns scenario
        coEvery { repository.save(any()) } returns Unit

        val result = service.approve(id, approvedBy = "risk-manager-1")

        result.category shouldBe ScenarioCategory.REGULATORY_MANDATED
    }

    test("category is preserved through retire lifecycle") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenarioWithCategory(
            id = id,
            status = ScenarioStatus.APPROVED,
            category = ScenarioCategory.REGULATORY_MANDATED,
        )
        coEvery { repository.findById(id) } returns scenario
        coEvery { repository.save(any()) } returns Unit

        val result = service.retire(id)

        result.category shouldBe ScenarioCategory.REGULATORY_MANDATED
    }
})

private fun aScenarioWithCategory(
    id: String = UUID.randomUUID().toString(),
    name: String = "Test Scenario",
    status: ScenarioStatus = ScenarioStatus.DRAFT,
    category: ScenarioCategory = ScenarioCategory.INTERNAL_APPROVED,
) = StressScenario(
    id = id,
    name = name,
    description = "Test description",
    shocks = """{"equity":-0.10}""",
    status = status,
    createdBy = "analyst-1",
    approvedBy = null,
    approvedAt = null,
    createdAt = Instant.now(),
    category = category,
)
