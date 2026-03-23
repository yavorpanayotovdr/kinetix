package com.kinetix.position

import com.kinetix.common.model.*
import com.kinetix.position.kafka.TradeEventPublisher
import com.kinetix.position.model.LimitDefinition
import com.kinetix.position.model.LimitLevel
import com.kinetix.position.model.LimitType
import com.kinetix.position.persistence.DatabaseTestSetup
import com.kinetix.position.persistence.ExposedLimitDefinitionRepository
import com.kinetix.position.persistence.ExposedPositionRepository
import com.kinetix.position.persistence.ExposedTemporaryLimitIncreaseRepository
import com.kinetix.position.persistence.ExposedTradeEventRepository
import com.kinetix.position.service.BookTradeCommand
import com.kinetix.position.service.ExposedTransactionalRunner
import com.kinetix.position.service.HierarchyBasedPreTradeCheckService
import com.kinetix.position.service.LimitBreachException
import com.kinetix.position.service.LimitHierarchyService
import com.kinetix.position.service.TradeBookingService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import java.util.UUID

private val USD = Currency.getInstance("USD")
private val TRADED_AT = Instant.parse("2025-01-15T10:00:00Z")

class LimitEnforcementAcceptanceTest : BehaviorSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val tradeRepo = ExposedTradeEventRepository(db)
    val positionRepo = ExposedPositionRepository(db)
    val transactional = ExposedTransactionalRunner(db)
    val limitDefinitionRepo = ExposedLimitDefinitionRepository(db)
    val temporaryLimitIncreaseRepo = ExposedTemporaryLimitIncreaseRepository(db)
    val limitHierarchyService = LimitHierarchyService(limitDefinitionRepo, temporaryLimitIncreaseRepo)

    fun preTradeCheck() = HierarchyBasedPreTradeCheckService(positionRepo, limitHierarchyService)

    beforeEach {
        newSuspendedTransaction(db = db) {
            exec("TRUNCATE TABLE trade_events, positions, limit_definitions, limit_temporary_increases RESTART IDENTITY CASCADE")
        }
    }

    // Scenario 10: notional limit breach
    given("a BOOK-level notional limit of \$200,000") {
        `when`("a trade with \$300,000 notional is submitted (3000 shares at \$100)") {
            then("LimitBreachException is thrown with NOTIONAL breach and no trade is persisted") {
                val publisher = mockk<TradeEventPublisher>(relaxed = true)
                limitDefinitionRepo.save(
                    LimitDefinition(
                        id = UUID.randomUUID().toString(),
                        level = LimitLevel.BOOK,
                        entityId = "port-notional-1",
                        limitType = LimitType.NOTIONAL,
                        limitValue = BigDecimal("200000"),
                        intradayLimit = null,
                        overnightLimit = null,
                        active = true,
                    ),
                )
                val service = TradeBookingService(tradeRepo, positionRepo, transactional, publisher, preTradeCheck())

                var notionalEx: LimitBreachException? = null
                try {
                    service.handle(
                        BookTradeCommand(
                            tradeId = TradeId("t-notional-1"),
                            bookId = BookId("port-notional-1"),
                            instrumentId = InstrumentId("AAPL"),
                            assetClass = AssetClass.EQUITY,
                            side = Side.BUY,
                            quantity = BigDecimal("3000"),
                            price = Money(BigDecimal("100.00"), USD),
                            tradedAt = TRADED_AT,
                        ),
                    )
                } catch (e: LimitBreachException) {
                    notionalEx = e
                }
                (notionalEx is LimitBreachException) shouldBe true
                notionalEx!!.result.blocked shouldBe true
                notionalEx.result.breaches.any { it.limitType == "NOTIONAL" } shouldBe true
                tradeRepo.findByTradeId(TradeId("t-notional-1")) shouldBe null
            }
        }
    }

    // Scenario 11: concentration limit breach
    given("a BOOK-level concentration limit of 50% with AAPL at 50% of portfolio by market value") {
        `when`("a trade is submitted that would push AAPL above 50% concentration") {
            // Portfolio: AAPL 4000 shares @ $100 market = $400K; MSFT 4000 shares @ $100 market = $400K
            // Total portfolio market value = $800K; AAPL = 50% (exactly at limit)
            // Trade: BUY 1 AAPL at $100 → newPortfolioValue = $800,100
            //   instrumentValue = 4001 * $100 = $400,100
            //   concentrationPct ≈ 50.006% > 50% → breach
            then("LimitBreachException is thrown with CONCENTRATION breach and no trade is persisted") {
                val publisher = mockk<TradeEventPublisher>(relaxed = true)
                limitDefinitionRepo.save(
                    LimitDefinition(
                        id = UUID.randomUUID().toString(),
                        level = LimitLevel.BOOK,
                        entityId = "port-conc-1",
                        limitType = LimitType.CONCENTRATION,
                        limitValue = BigDecimal("0.5"),
                        intradayLimit = null,
                        overnightLimit = null,
                        active = true,
                    ),
                )
                val service = TradeBookingService(tradeRepo, positionRepo, transactional, publisher, preTradeCheck())

                positionRepo.save(
                    Position(
                        bookId = BookId("port-conc-1"),
                        instrumentId = InstrumentId("AAPL"),
                        assetClass = AssetClass.EQUITY,
                        quantity = BigDecimal("4000"),
                        averageCost = Money(BigDecimal("100.00"), USD),
                        marketPrice = Money(BigDecimal("100.00"), USD),
                    ),
                )
                positionRepo.save(
                    Position(
                        bookId = BookId("port-conc-1"),
                        instrumentId = InstrumentId("MSFT"),
                        assetClass = AssetClass.EQUITY,
                        quantity = BigDecimal("4000"),
                        averageCost = Money(BigDecimal("100.00"), USD),
                        marketPrice = Money(BigDecimal("100.00"), USD),
                    ),
                )

                var concEx: LimitBreachException? = null
                try {
                    service.handle(
                        BookTradeCommand(
                            tradeId = TradeId("t-conc-1"),
                            bookId = BookId("port-conc-1"),
                            instrumentId = InstrumentId("AAPL"),
                            assetClass = AssetClass.EQUITY,
                            side = Side.BUY,
                            quantity = BigDecimal("1"),
                            price = Money(BigDecimal("100.00"), USD),
                            tradedAt = TRADED_AT,
                        ),
                    )
                } catch (e: LimitBreachException) {
                    concEx = e
                }
                (concEx is LimitBreachException) shouldBe true
                concEx!!.result.blocked shouldBe true
                concEx.result.breaches.any { it.limitType == "CONCENTRATION" } shouldBe true
                tradeRepo.findByTradeId(TradeId("t-conc-1")) shouldBe null
            }
        }
    }
})
