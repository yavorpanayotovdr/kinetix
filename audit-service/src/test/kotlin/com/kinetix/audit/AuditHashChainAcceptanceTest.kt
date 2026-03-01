package com.kinetix.audit

import com.kinetix.audit.model.AuditEvent
import com.kinetix.audit.persistence.AuditEventRepository
import com.kinetix.audit.persistence.AuditHasher
import com.kinetix.audit.persistence.DatabaseTestSetup
import com.kinetix.audit.persistence.ExposedAuditEventRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

private val BASE_TIME: Instant = Instant.parse("2026-01-15T10:00:00Z")

private fun tradeEvent(
    tradeId: String = "t-1",
    portfolioId: String = "port-1",
    instrumentId: String = "AAPL",
    assetClass: String = "EQUITY",
    side: String = "BUY",
    quantity: String = "100",
    priceAmount: String = "150.00",
    priceCurrency: String = "USD",
    tradedAt: String = "2026-01-15T10:00:00Z",
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

class AuditHashChainAcceptanceTest : BehaviorSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: AuditEventRepository = ExposedAuditEventRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) {
            exec("TRUNCATE TABLE audit_events RESTART IDENTITY")
        }
    }

    given("5 trade events published in sequence") {
        `when`("all 5 are saved") {
            then("all have non-null recordHash, verify returns valid=true, and each previousHash links correctly") {
                repeat(5) { i ->
                    repository.save(tradeEvent(tradeId = "t-${i + 1}", receivedAt = BASE_TIME.plusSeconds(i.toLong())))
                }

                val events = repository.findAll()
                events.size shouldBe 5

                events.forEach { event ->
                    event.recordHash.shouldNotBeNull()
                    event.recordHash.length shouldBe 64
                }

                // First event has no previous
                events[0].previousHash shouldBe null

                // Each subsequent event's previousHash equals the preceding event's recordHash
                for (i in 1 until events.size) {
                    events[i].previousHash shouldBe events[i - 1].recordHash
                }

                val result = AuditHasher.verifyChain(events)
                result.valid shouldBe true
                result.eventCount shouldBe 5
            }
        }
    }

    given("3 audit events with valid hash links") {
        `when`("the priceAmount of the second event is modified directly in the database") {
            then("verifyChain returns valid=false") {
                repeat(3) { i ->
                    repository.save(tradeEvent(tradeId = "t-${i + 1}", receivedAt = BASE_TIME.plusSeconds(i.toLong())))
                }

                val before = repository.findAll()
                before.size shouldBe 3
                AuditHasher.verifyChain(before).valid shouldBe true

                val secondId = before[1].id

                // Drop triggers, tamper, re-create triggers to bypass immutability protection
                newSuspendedTransaction(db = db) {
                    exec("DROP TRIGGER IF EXISTS prevent_audit_update ON audit_events")
                    exec(
                        "UPDATE audit_events SET price_amount = 9999.00 WHERE id = $secondId"
                    )
                    exec(
                        """
                        CREATE TRIGGER prevent_audit_update
                            BEFORE UPDATE ON audit_events
                            FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation()
                        """.trimIndent()
                    )
                }

                val after = repository.findAll()
                val result = AuditHasher.verifyChain(after)
                result.valid shouldBe false
            }
        }
    }

    given("a trade event with userId and userRole") {
        `when`("the event is persisted") {
            then("userId and userRole are stored correctly, and modifying userId causes verifyChain to fail") {
                repository.save(
                    tradeEvent(
                        tradeId = "t-marcus",
                        userId = "trader-marcus",
                        userRole = "TRADER",
                    )
                )

                val events = repository.findAll()
                events.size shouldBe 1

                events[0].userId shouldBe "trader-marcus"
                events[0].userRole shouldBe "TRADER"

                // Verify the chain is initially valid
                AuditHasher.verifyChain(events).valid shouldBe true

                val eventId = events[0].id

                // Tamper with userId — drop trigger, modify, re-create
                newSuspendedTransaction(db = db) {
                    exec("DROP TRIGGER IF EXISTS prevent_audit_update ON audit_events")
                    exec(
                        "UPDATE audit_events SET user_id = 'attacker' WHERE id = $eventId"
                    )
                    exec(
                        """
                        CREATE TRIGGER prevent_audit_update
                            BEFORE UPDATE ON audit_events
                            FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation()
                        """.trimIndent()
                    )
                }

                val tampered = repository.findAll()
                tampered[0].userId shouldBe "attacker"

                val result = AuditHasher.verifyChain(tampered)
                result.valid shouldBe false
            }
        }
    }
})
