package com.kinetix.notification.engine

import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.AlertStatus
import com.kinetix.notification.model.AlertType
import com.kinetix.notification.model.Severity
import com.kinetix.notification.persistence.InMemoryAlertEventRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant

class ScheduledAlertEscalationTest : FunSpec({

    test("tick delegates to escalation service with current time") {
        val escalationService = mockk<AlertEscalationService>(relaxed = true)
        val scheduler = ScheduledAlertEscalation(escalationService)

        val instantSlot = slot<Instant>()
        val before = Instant.now()
        scheduler.tick()
        val after = Instant.now()

        coVerify(exactly = 1) { escalationService.processEscalations(capture(instantSlot)) }
        val captured = instantSlot.captured
        // The captured instant must be between before and after
        captured.isBefore(before).shouldBe(false)
        captured.isAfter(after).shouldBe(false)
    }

    test("multiple ticks each invoke escalation service once per tick") {
        val escalationService = mockk<AlertEscalationService>(relaxed = true)
        val scheduler = ScheduledAlertEscalation(escalationService)

        scheduler.tick()
        scheduler.tick()
        scheduler.tick()

        coVerify(exactly = 3) { escalationService.processEscalations(any()) }
    }
})
