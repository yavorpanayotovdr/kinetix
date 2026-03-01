package com.kinetix.audit

import com.kinetix.audit.model.AuditEvent
import com.kinetix.audit.persistence.AuditEventRepository
import com.kinetix.audit.persistence.DatabaseTestSetup
import com.kinetix.audit.persistence.ExposedAuditEventRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

private val BASE_TIME: Instant = Instant.parse("2026-02-01T09:00:00Z")

private fun tradeEvent(
    tradeId: String = "t-1",
    portfolioId: String = "port-1",
    instrumentId: String = "AAPL",
    assetClass: String = "EQUITY",
    side: String = "BUY",
    quantity: String = "100",
    priceAmount: String = "150.00",
    priceCurrency: String = "USD",
    tradedAt: String = "2026-02-01T09:00:00Z",
    receivedAt: Instant = BASE_TIME,
    userId: String? = null,
    userRole: String? = null,
    eventType: String = "TRADE_BOOKED",
) = AuditEvent(
    tradeId = tradeId,
    portfolioId = portfolioId,
    instrumentId = instrumentId,
    assetClass = assetClass,
    side = side,
    quantity = quantity,
    priceAmount = priceAmount,
    priceCurrency = priceCurrency,
    tradedAt = tradedAt,
    receivedAt = receivedAt,
    userId = userId,
    userRole = userRole,
    eventType = eventType,
)

class AuditEventConsumptionAcceptanceTest : BehaviorSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: AuditEventRepository = ExposedAuditEventRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) {
            exec("TRUNCATE TABLE audit_events RESTART IDENTITY")
        }
    }

    given("audit events for portfolioId='port-1' and portfolioId='port-2'") {
        `when`("queried by portfolioId='port-1'") {
            then("only port-1 events are returned") {
                repository.save(tradeEvent(tradeId = "t-1", portfolioId = "port-1", receivedAt = BASE_TIME))
                repository.save(tradeEvent(tradeId = "t-2", portfolioId = "port-2", receivedAt = BASE_TIME.plusSeconds(1)))
                repository.save(tradeEvent(tradeId = "t-3", portfolioId = "port-1", receivedAt = BASE_TIME.plusSeconds(2)))
                repository.save(tradeEvent(tradeId = "t-4", portfolioId = "port-2", receivedAt = BASE_TIME.plusSeconds(3)))
                repository.save(tradeEvent(tradeId = "t-5", portfolioId = "port-1", receivedAt = BASE_TIME.plusSeconds(4)))

                val results = repository.findByPortfolioId("port-1")

                results shouldHaveSize 3
                results.forEach { it.portfolioId shouldBe "port-1" }
                results.map { it.tradeId } shouldBe listOf("t-1", "t-3", "t-5")
            }
        }

        `when`("queried by portfolioId='port-2'") {
            then("only port-2 events are returned") {
                repository.save(tradeEvent(tradeId = "t-1", portfolioId = "port-1", receivedAt = BASE_TIME))
                repository.save(tradeEvent(tradeId = "t-2", portfolioId = "port-2", receivedAt = BASE_TIME.plusSeconds(1)))
                repository.save(tradeEvent(tradeId = "t-3", portfolioId = "port-1", receivedAt = BASE_TIME.plusSeconds(2)))

                val results = repository.findByPortfolioId("port-2")

                results shouldHaveSize 1
                results[0].portfolioId shouldBe "port-2"
                results[0].tradeId shouldBe "t-2"
            }
        }

        `when`("queried by an unknown portfolioId") {
            then("an empty list is returned") {
                repository.save(tradeEvent(tradeId = "t-1", portfolioId = "port-1"))

                val results = repository.findByPortfolioId("port-unknown")

                results shouldHaveSize 0
            }
        }
    }

    given("events with different eventTypes: TRADE_BOOKED, TRADE_AMENDED, TRADE_CANCELLED") {
        `when`("all three are saved") {
            then("each is stored with the correct eventType field") {
                repository.save(
                    tradeEvent(tradeId = "t-booked", eventType = "TRADE_BOOKED", receivedAt = BASE_TIME)
                )
                repository.save(
                    tradeEvent(tradeId = "t-amended", eventType = "TRADE_AMENDED", receivedAt = BASE_TIME.plusSeconds(1))
                )
                repository.save(
                    tradeEvent(tradeId = "t-cancelled", eventType = "TRADE_CANCELLED", receivedAt = BASE_TIME.plusSeconds(2))
                )

                val all = repository.findAll()

                all shouldHaveSize 3

                val byTradeId = all.associateBy { it.tradeId }
                byTradeId["t-booked"]!!.eventType shouldBe "TRADE_BOOKED"
                byTradeId["t-amended"]!!.eventType shouldBe "TRADE_AMENDED"
                byTradeId["t-cancelled"]!!.eventType shouldBe "TRADE_CANCELLED"
            }
        }
    }
})
