package com.kinetix.audit

import com.kinetix.audit.model.AuditEvent
import com.kinetix.audit.persistence.AuditEventRepository
import com.kinetix.audit.persistence.AuditHasher
import com.kinetix.audit.persistence.DatabaseTestSetup
import com.kinetix.audit.persistence.ExposedAuditEventRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

private val BASE = Instant.parse("2026-03-01T09:00:00Z")

private fun tradeAuditEvent(
    tradeId: String,
    bookId: String = "port-1",
    receivedAt: Instant = BASE,
) = AuditEvent(
    tradeId = tradeId,
    bookId = bookId,
    instrumentId = "AAPL",
    assetClass = "EQUITY",
    side = "BUY",
    quantity = "100",
    priceAmount = "150.00",
    priceCurrency = "USD",
    tradedAt = "2026-03-01T09:00:00Z",
    receivedAt = receivedAt,
    userId = "trader-1",
    userRole = "TRADER",
    eventType = "TRADE_BOOKED",
)

private fun governanceAuditEvent(
    eventType: String,
    userId: String = "risk-mgr-1",
    userRole: String = "RISK_MANAGER",
    modelName: String? = null,
    scenarioId: String? = null,
    bookId: String? = null,
    details: String? = null,
    receivedAt: Instant = BASE,
) = AuditEvent(
    tradeId = null,
    bookId = bookId,
    instrumentId = null,
    assetClass = null,
    side = null,
    quantity = null,
    priceAmount = null,
    priceCurrency = null,
    tradedAt = null,
    receivedAt = receivedAt,
    userId = userId,
    userRole = userRole,
    eventType = eventType,
    modelName = modelName,
    scenarioId = scenarioId,
    details = details,
)

class GovernanceAuditAcceptanceTest : BehaviorSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: AuditEventRepository = ExposedAuditEventRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) {
            exec("TRUNCATE TABLE audit_events RESTART IDENTITY")
        }
    }

    given("a mix of trade and governance audit events") {
        `when`("all are saved in sequence") {
            then("hash chain spans both event types and verifies as valid") {
                repository.save(tradeAuditEvent("t-1", receivedAt = BASE))
                repository.save(
                    governanceAuditEvent(
                        "MODEL_STATUS_CHANGED",
                        modelName = "VaR-v2",
                        details = "DRAFT->VALIDATED",
                        receivedAt = BASE.plusSeconds(1),
                    )
                )
                repository.save(tradeAuditEvent("t-2", receivedAt = BASE.plusSeconds(2)))
                repository.save(
                    governanceAuditEvent(
                        "SCENARIO_APPROVED",
                        scenarioId = "sc-001",
                        details = "approved",
                        receivedAt = BASE.plusSeconds(3),
                    )
                )

                val all = repository.findAll()
                all shouldHaveSize 4

                val result = AuditHasher.verifyChain(all)
                result.valid shouldBe true
                result.eventCount shouldBe 4
            }
        }
    }

    given("a governance event with null trade fields") {
        `when`("saved and read back") {
            then("trade fields are null and governance fields are populated") {
                repository.save(
                    governanceAuditEvent(
                        eventType = "LIMIT_BREACHED",
                        userId = "system",
                        userRole = "SYSTEM",
                        details = "VaR limit breached: 12.5M > 10M",
                        receivedAt = BASE,
                    )
                )

                val events = repository.findAll()
                events shouldHaveSize 1

                val event = events[0]
                event.tradeId.shouldBeNull()
                event.instrumentId.shouldBeNull()
                event.assetClass.shouldBeNull()
                event.side.shouldBeNull()
                event.quantity.shouldBeNull()
                event.priceAmount.shouldBeNull()
                event.priceCurrency.shouldBeNull()
                event.tradedAt.shouldBeNull()
                event.eventType shouldBe "LIMIT_BREACHED"
                event.details shouldBe "VaR limit breached: 12.5M > 10M"
                event.recordHash.shouldNotBeNull()
                event.recordHash.length shouldBe 64
            }
        }
    }

    given("a trade event followed by a governance event") {
        `when`("the governance event scenarioId is tampered") {
            then("verifyChain returns false") {
                repository.save(tradeAuditEvent("t-1", receivedAt = BASE))
                repository.save(
                    governanceAuditEvent(
                        "SCENARIO_APPROVED",
                        scenarioId = "sc-real",
                        receivedAt = BASE.plusSeconds(1),
                    )
                )

                val before = repository.findAll()
                AuditHasher.verifyChain(before).valid shouldBe true

                val govId = before[1].id
                newSuspendedTransaction(db = db) {
                    exec("DROP TRIGGER IF EXISTS prevent_audit_update ON audit_events")
                    exec("UPDATE audit_events SET scenario_id = 'sc-fake' WHERE id = $govId")
                    exec(
                        """
                        CREATE TRIGGER prevent_audit_update
                            BEFORE UPDATE ON audit_events
                            FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation()
                        """.trimIndent()
                    )
                }

                val after = repository.findAll()
                AuditHasher.verifyChain(after).valid shouldBe false
            }
        }
    }

    given("governance events filtered by bookId") {
        `when`("saved with a specific bookId") {
            then("findByBookId returns only matching events") {
                repository.save(
                    governanceAuditEvent(
                        "RISK_CALCULATION_COMPLETED",
                        bookId = "book-A",
                        receivedAt = BASE,
                    )
                )
                repository.save(
                    governanceAuditEvent(
                        "EOD_PROMOTED",
                        bookId = "book-B",
                        receivedAt = BASE.plusSeconds(1),
                    )
                )
                repository.save(
                    governanceAuditEvent(
                        "RISK_CALCULATION_COMPLETED",
                        bookId = "book-A",
                        receivedAt = BASE.plusSeconds(2),
                    )
                )

                val bookA = repository.findByBookId("book-A")
                bookA shouldHaveSize 2
                bookA.all { it.bookId == "book-A" } shouldBe true
            }
        }
    }
})
