package com.kinetix.regulatory.governance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.util.UUID

class ModelRegistryTest : FunSpec({

    val repository = mockk<ModelVersionRepository>()
    val service = ModelRegistry(repository)

    test("registers a new model version") {
        val captured = slot<ModelVersion>()
        coEvery { repository.save(capture(captured)) } returns Unit

        val result = service.register(
            modelName = "HistoricalVaR",
            version = "1.0.0",
            parameters = """{"window":250,"confidenceLevel":0.99}""",
        )

        result.modelName shouldBe "HistoricalVaR"
        result.version shouldBe "1.0.0"
        result.status shouldBe ModelVersionStatus.DRAFT
        result.parameters shouldBe """{"window":250,"confidenceLevel":0.99}"""
        result.approvedBy shouldBe null
        result.approvedAt shouldBe null

        coVerify(exactly = 1) { repository.save(any()) }
    }

    test("lists all model versions") {
        val models = listOf(
            aModelVersion(modelName = "HistoricalVaR", version = "1.0.0"),
            aModelVersion(modelName = "HistoricalVaR", version = "2.0.0"),
            aModelVersion(modelName = "MonteCarloVaR", version = "1.0.0"),
        )
        coEvery { repository.findAll() } returns models

        val result = service.listAll()

        result shouldHaveSize 3
    }

    test("retrieves model version by id") {
        val id = UUID.randomUUID().toString()
        val model = aModelVersion(id = id, modelName = "HistoricalVaR", version = "1.0.0")
        coEvery { repository.findById(id) } returns model

        val result = service.findById(id)

        result shouldNotBe null
        result!!.id shouldBe id
        result.modelName shouldBe "HistoricalVaR"
    }

    test("tracks model status transitions from DRAFT to VALIDATED") {
        val id = UUID.randomUUID().toString()
        val model = aModelVersion(id = id, status = ModelVersionStatus.DRAFT)
        coEvery { repository.findById(id) } returns model
        coEvery { repository.save(any()) } returns Unit

        val result = service.transitionStatus(id, ModelVersionStatus.VALIDATED, approvedBy = null)

        result.status shouldBe ModelVersionStatus.VALIDATED
    }

    test("tracks model status transitions from VALIDATED to APPROVED") {
        val id = UUID.randomUUID().toString()
        val model = aModelVersion(id = id, status = ModelVersionStatus.VALIDATED)
        coEvery { repository.findById(id) } returns model
        coEvery { repository.save(any()) } returns Unit

        val result = service.transitionStatus(id, ModelVersionStatus.APPROVED, approvedBy = "risk-manager-1")

        result.status shouldBe ModelVersionStatus.APPROVED
        result.approvedBy shouldBe "risk-manager-1"
        result.approvedAt shouldNotBe null
    }

    test("tracks model status transitions from APPROVED to RETIRED") {
        val id = UUID.randomUUID().toString()
        val model = aModelVersion(id = id, status = ModelVersionStatus.APPROVED)
        coEvery { repository.findById(id) } returns model
        coEvery { repository.save(any()) } returns Unit

        val result = service.transitionStatus(id, ModelVersionStatus.RETIRED, approvedBy = null)

        result.status shouldBe ModelVersionStatus.RETIRED
    }

    test("rejects invalid status transition from DRAFT to APPROVED") {
        val id = UUID.randomUUID().toString()
        val model = aModelVersion(id = id, status = ModelVersionStatus.DRAFT)
        coEvery { repository.findById(id) } returns model

        shouldThrow<IllegalStateException> {
            service.transitionStatus(id, ModelVersionStatus.APPROVED, approvedBy = "risk-manager-1")
        }
    }

    test("rejects invalid status transition from RETIRED to DRAFT") {
        val id = UUID.randomUUID().toString()
        val model = aModelVersion(id = id, status = ModelVersionStatus.RETIRED)
        coEvery { repository.findById(id) } returns model

        shouldThrow<IllegalStateException> {
            service.transitionStatus(id, ModelVersionStatus.DRAFT, approvedBy = null)
        }
    }

    test("throws when model version not found") {
        coEvery { repository.findById(any()) } returns null

        shouldThrow<NoSuchElementException> {
            service.transitionStatus("non-existent", ModelVersionStatus.VALIDATED, approvedBy = null)
        }
    }
})

private fun aModelVersion(
    id: String = UUID.randomUUID().toString(),
    modelName: String = "TestModel",
    version: String = "1.0.0",
    status: ModelVersionStatus = ModelVersionStatus.DRAFT,
    parameters: String = "{}",
    approvedBy: String? = null,
    approvedAt: Instant? = null,
    createdAt: Instant = Instant.now(),
) = ModelVersion(
    id = id,
    modelName = modelName,
    version = version,
    status = status,
    parameters = parameters,
    approvedBy = approvedBy,
    approvedAt = approvedAt,
    createdAt = createdAt,
)
