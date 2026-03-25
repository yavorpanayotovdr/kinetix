package com.kinetix.common.audit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GovernanceAuditEventTest : FunSpec({

    test("constructs with required fields only") {
        val event = GovernanceAuditEvent(
            eventType = AuditEventType.MODEL_STATUS_CHANGED,
            userId = "user-1",
            userRole = "RISK_MANAGER",
        )

        event.eventType shouldBe AuditEventType.MODEL_STATUS_CHANGED
        event.userId shouldBe "user-1"
        event.userRole shouldBe "RISK_MANAGER"
        event.modelName.shouldBeNull()
        event.scenarioId.shouldBeNull()
        event.limitId.shouldBeNull()
        event.submissionId.shouldBeNull()
        event.bookId.shouldBeNull()
        event.details.shouldBeNull()
    }

    test("constructs with all optional fields") {
        val event = GovernanceAuditEvent(
            eventType = AuditEventType.SCENARIO_APPROVED,
            userId = "approver-1",
            userRole = "HEAD_OF_RISK",
            modelName = "VaR-v2",
            scenarioId = "scenario-abc",
            limitId = "limit-xyz",
            submissionId = "sub-001",
            bookId = "BOOK-A",
            details = "approved after review",
        )

        event.scenarioId shouldBe "scenario-abc"
        event.modelName shouldBe "VaR-v2"
        event.details shouldBe "approved after review"
    }

    test("serializes and deserializes via JSON without data loss") {
        val event = GovernanceAuditEvent(
            eventType = AuditEventType.RBAC_ACCESS_DENIED,
            userId = "user-x",
            userRole = "TRADER",
            details = "attempted to access admin endpoint",
        )

        val json = Json.encodeToString(event)
        val decoded = Json.decodeFromString<GovernanceAuditEvent>(json)

        decoded shouldBe event
    }
})
