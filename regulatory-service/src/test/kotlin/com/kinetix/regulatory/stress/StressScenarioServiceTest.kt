package com.kinetix.regulatory.stress

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class StressScenarioServiceTest : FunSpec({

    val repository = mockk<StressScenarioRepository>()
    val resultRepository = mockk<StressTestResultRepository>()
    val service = StressScenarioService(repository)

    test("creates a scenario in DRAFT status") {
        coEvery { repository.save(any()) } returns Unit

        val result = service.create(
            name = "2008 Financial Crisis",
            description = "Replicates 2008 market conditions",
            shocks = """{"equity":-0.40,"credit_spread":0.05,"fx":-0.15}""",
            createdBy = "risk-analyst-1",
        )

        result.name shouldBe "2008 Financial Crisis"
        result.description shouldBe "Replicates 2008 market conditions"
        result.shocks shouldBe """{"equity":-0.40,"credit_spread":0.05,"fx":-0.15}"""
        result.status shouldBe ScenarioStatus.DRAFT
        result.createdBy shouldBe "risk-analyst-1"
        result.approvedBy shouldBe null
        result.approvedAt shouldBe null

        coVerify(exactly = 1) { repository.save(any()) }
    }

    test("approves a scenario") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.PENDING_APPROVAL)
        coEvery { repository.findById(id) } returns scenario
        coEvery { repository.save(any()) } returns Unit

        val result = service.approve(id, approvedBy = "risk-manager-1")

        result.status shouldBe ScenarioStatus.APPROVED
        result.approvedBy shouldBe "risk-manager-1"
        result.approvedAt shouldNotBe null
    }

    test("lists only approved scenarios") {
        val scenarios = listOf(
            aScenario(name = "Scenario A", status = ScenarioStatus.APPROVED),
            aScenario(name = "Scenario B", status = ScenarioStatus.APPROVED),
        )
        coEvery { repository.findByStatus(ScenarioStatus.APPROVED) } returns scenarios

        val result = service.listApproved()

        result shouldHaveSize 2
        result.forEach { it.status shouldBe ScenarioStatus.APPROVED }
    }

    test("retires a scenario") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.APPROVED)
        coEvery { repository.findById(id) } returns scenario
        coEvery { repository.save(any()) } returns Unit

        val result = service.retire(id)

        result.status shouldBe ScenarioStatus.RETIRED
    }

    test("submits scenario for approval") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.DRAFT)
        coEvery { repository.findById(id) } returns scenario
        coEvery { repository.save(any()) } returns Unit

        val result = service.submitForApproval(id)

        result.status shouldBe ScenarioStatus.PENDING_APPROVAL
    }

    test("rejects approval when not in PENDING_APPROVAL status") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.DRAFT)
        coEvery { repository.findById(id) } returns scenario

        shouldThrow<IllegalStateException> {
            service.approve(id, approvedBy = "risk-manager-1")
        }
    }

    test("rejects retire when not in APPROVED status") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.DRAFT)
        coEvery { repository.findById(id) } returns scenario

        shouldThrow<IllegalStateException> {
            service.retire(id)
        }
    }

    test("throws when scenario not found") {
        coEvery { repository.findById(any()) } returns null

        shouldThrow<NoSuchElementException> {
            service.approve("non-existent", approvedBy = "risk-manager-1")
        }
    }

    test("lists all scenarios") {
        val scenarios = listOf(
            aScenario(name = "Scenario A"),
            aScenario(name = "Scenario B"),
        )
        coEvery { repository.findAll() } returns scenarios

        val result = service.listAll()

        result shouldHaveSize 2
    }

    test("runScenario computes pnlImpact from shocks and persists result") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.APPROVED, shocks = """{"EQ":-0.30,"IR":0.01}""")
        val serviceWithResultRepo = StressScenarioService(repository, resultRepository)
        coEvery { repository.findById(id) } returns scenario
        coEvery { resultRepository.save(any()) } returns Unit

        val result = serviceWithResultRepo.runScenario(id, "portfolio-1", null)

        result.scenarioId shouldBe id
        result.portfolioId shouldBe "portfolio-1"
        result.pnlImpact shouldBe BigDecimal.valueOf(-0.30 + 0.01)
        coVerify(exactly = 1) { resultRepository.save(any()) }
    }

    test("runScenario rejects non-APPROVED scenario") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.DRAFT)
        coEvery { repository.findById(id) } returns scenario

        shouldThrow<IllegalStateException> {
            service.runScenario(id, "portfolio-1", null)
        }
    }

    test("runScenario without resultRepository does not persist") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.APPROVED, shocks = """{"EQ":-0.10}""")
        coEvery { repository.findById(id) } returns scenario

        val result = service.runScenario(id, "portfolio-1", null)

        result.pnlImpact shouldBe BigDecimal.valueOf(-0.10)
    }
})

private fun aScenario(
    id: String = UUID.randomUUID().toString(),
    name: String = "Test Scenario",
    description: String = "Test description",
    shocks: String = """{"equity":-0.20}""",
    status: ScenarioStatus = ScenarioStatus.DRAFT,
    createdBy: String = "risk-analyst-1",
    approvedBy: String? = null,
    approvedAt: Instant? = null,
    createdAt: Instant = Instant.now(),
) = StressScenario(
    id = id,
    name = name,
    description = description,
    shocks = shocks,
    status = status,
    createdBy = createdBy,
    approvedBy = approvedBy,
    approvedAt = approvedAt,
    createdAt = createdAt,
)
