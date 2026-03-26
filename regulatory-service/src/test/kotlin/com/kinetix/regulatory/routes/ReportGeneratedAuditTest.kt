package com.kinetix.regulatory.routes

import com.kinetix.common.audit.AuditEventType
import com.kinetix.common.audit.GovernanceAuditEvent
import com.kinetix.regulatory.audit.GovernanceAuditPublisher
import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.dto.FrtbResultResponse
import com.kinetix.regulatory.dto.RiskClassChargeDto
import com.kinetix.regulatory.module
import com.kinetix.regulatory.persistence.BacktestResultRepository
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.concurrent.Future

class ReportGeneratedAuditTest : FunSpec({

    val frtbRepo = mockk<FrtbCalculationRepository>()
    val backtestRepo = mockk<BacktestResultRepository>()
    val riskClient = mockk<RiskOrchestratorClient>()
    val producer = mockk<KafkaProducer<String, String>>()
    val auditPublisher = GovernanceAuditPublisher(producer, topic = "governance.audit")

    val sampleFrtbResult = FrtbResultResponse(
        bookId = "port-1",
        sbmCharges = listOf(RiskClassChargeDto("GIRR", "100.00", "50.00", "25.00", "175.00")),
        totalSbmCharge = "175.00",
        grossJtd = "200.00",
        hedgeBenefit = "50.00",
        netDrc = "150.00",
        exoticNotional = "10.00",
        otherNotional = "5.00",
        totalRrao = "15.00",
        totalCapitalCharge = "340.00",
        calculatedAt = "2025-01-15T10:00:00Z",
    )

    fun givenFuture(): Future<RecordMetadata> = mockk(relaxed = true)

    beforeEach {
        clearMocks(frtbRepo, backtestRepo, riskClient, producer)
    }

    test("publishes REPORT_GENERATED audit event after FRTB calculation completes") {
        coEvery { riskClient.calculateFrtb("port-1") } returns sampleFrtbResult
        coEvery { frtbRepo.save(any()) } returns Unit

        val publishedSlot = slot<org.apache.kafka.clients.producer.ProducerRecord<String, String>>()
        every { producer.send(capture(publishedSlot)) } returns givenFuture()

        testApplication {
            application { module(frtbRepo, riskClient, auditPublisher = auditPublisher) }
            client.post("/api/v1/regulatory/frtb/port-1/calculate")
        }

        verify(atLeast = 1) { producer.send(any()) }
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString<GovernanceAuditEvent>(publishedSlot.captured.value())
        decoded.eventType shouldBe AuditEventType.REPORT_GENERATED
        decoded.bookId shouldBe "port-1"
        decoded.details shouldBe "FRTB"
    }

    test("publishes REPORT_GENERATED audit event after backtest completes") {
        coEvery { backtestRepo.save(any()) } returns Unit

        val publishedSlot = slot<org.apache.kafka.clients.producer.ProducerRecord<String, String>>()
        every { producer.send(capture(publishedSlot)) } returns givenFuture()

        testApplication {
            application { module(frtbRepo, riskClient, backtestRepository = backtestRepo, auditPublisher = auditPublisher) }
            client.post("/api/v1/regulatory/backtest/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"dailyVarPredictions":[100.0,100.0,100.0],"dailyPnl":[-50.0,-150.0,-80.0],"confidenceLevel":0.99}""")
            }
        }

        verify(atLeast = 1) { producer.send(any()) }
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString<GovernanceAuditEvent>(publishedSlot.captured.value())
        decoded.eventType shouldBe AuditEventType.REPORT_GENERATED
        decoded.bookId shouldBe "port-1"
        decoded.details shouldBe "BACKTEST"
    }

    test("does not publish audit event when audit publisher is not configured") {
        coEvery { riskClient.calculateFrtb("port-1") } returns sampleFrtbResult
        coEvery { frtbRepo.save(any()) } returns Unit

        testApplication {
            application { module(frtbRepo, riskClient) }
            client.post("/api/v1/regulatory/frtb/port-1/calculate")
        }

        verify(exactly = 0) { producer.send(any()) }
    }
})
