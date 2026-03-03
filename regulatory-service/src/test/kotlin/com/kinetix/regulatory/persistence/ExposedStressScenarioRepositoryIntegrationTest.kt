package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.stress.ScenarioStatus
import com.kinetix.regulatory.stress.StressScenario
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun aScenario(
    id: String = UUID.randomUUID().toString(),
    name: String = "Interest Rate Shock",
    description: String = "Parallel shift +200bps",
    shocks: String = """{"IR":0.02}""",
    status: ScenarioStatus = ScenarioStatus.DRAFT,
    createdBy: String = "analyst@kinetix.com",
    approvedBy: String? = null,
    approvedAt: Instant? = null,
    createdAt: Instant = NOW,
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

class ExposedStressScenarioRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository = ExposedStressScenarioRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) { StressScenariosTable.deleteAll() }
    }

    test("should save and retrieve a scenario by id") {
        val scenario = aScenario(id = "sc-1")
        repository.save(scenario)

        val found = repository.findById("sc-1")
        found.shouldNotBeNull()
        found.id shouldBe "sc-1"
        found.name shouldBe "Interest Rate Shock"
        found.description shouldBe "Parallel shift +200bps"
        found.shocks shouldBe """{"IR":0.02}"""
        found.status shouldBe ScenarioStatus.DRAFT
        found.createdBy shouldBe "analyst@kinetix.com"
        found.approvedBy.shouldBeNull()
        found.approvedAt.shouldBeNull()
    }

    test("findById returns null for unknown id") {
        repository.findById("nonexistent").shouldBeNull()
    }

    test("findAll returns scenarios ordered by createdAt descending") {
        repository.save(aScenario(id = "sc-oldest", name = "Oldest", createdAt = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(aScenario(id = "sc-middle", name = "Middle", createdAt = Instant.parse("2026-01-15T10:00:00Z")))
        repository.save(aScenario(id = "sc-newest", name = "Newest", createdAt = Instant.parse("2026-01-20T10:00:00Z")))

        val all = repository.findAll()
        all shouldHaveSize 3
        all[0].name shouldBe "Newest"
        all[1].name shouldBe "Middle"
        all[2].name shouldBe "Oldest"
    }

    test("findByStatus filters correctly") {
        repository.save(aScenario(id = "sc-draft", status = ScenarioStatus.DRAFT))
        repository.save(aScenario(id = "sc-approved", status = ScenarioStatus.APPROVED))
        repository.save(aScenario(id = "sc-retired", status = ScenarioStatus.RETIRED))

        val drafts = repository.findByStatus(ScenarioStatus.DRAFT)
        drafts shouldHaveSize 1
        drafts[0].id shouldBe "sc-draft"

        val approved = repository.findByStatus(ScenarioStatus.APPROVED)
        approved shouldHaveSize 1
        approved[0].id shouldBe "sc-approved"
    }

    test("save updates existing scenario on upsert") {
        val original = aScenario(id = "sc-upsert", status = ScenarioStatus.DRAFT)
        repository.save(original)

        val approvedAt = Instant.parse("2026-01-16T12:00:00Z")
        val updated = original.copy(
            status = ScenarioStatus.APPROVED,
            approvedBy = "manager@kinetix.com",
            approvedAt = approvedAt,
        )
        repository.save(updated)

        val found = repository.findById("sc-upsert")
        found.shouldNotBeNull()
        found.status shouldBe ScenarioStatus.APPROVED
        found.approvedBy shouldBe "manager@kinetix.com"
        found.approvedAt shouldBe approvedAt
        found.name shouldBe "Interest Rate Shock"
    }

    test("findAll returns empty list when no scenarios exist") {
        repository.findAll() shouldHaveSize 0
    }
})
