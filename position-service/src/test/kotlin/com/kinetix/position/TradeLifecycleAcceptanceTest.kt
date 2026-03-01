package com.kinetix.position

import com.kinetix.common.model.*
import com.kinetix.position.kafka.TradeEventPublisher
import com.kinetix.position.persistence.DatabaseTestSetup
import com.kinetix.position.persistence.ExposedPositionRepository
import com.kinetix.position.persistence.ExposedTradeEventRepository
import com.kinetix.position.service.AmendTradeCommand
import com.kinetix.position.service.BookTradeCommand
import com.kinetix.position.service.CancelTradeCommand
import com.kinetix.position.service.ExposedTransactionalRunner
import com.kinetix.position.service.TradeBookingService
import com.kinetix.position.service.TradeLifecycleService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import com.kinetix.position.persistence.PositionsTable
import com.kinetix.position.persistence.TradeEventsTable
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val TRADED_AT = Instant.parse("2025-01-15T10:00:00Z")

class TradeLifecycleAcceptanceTest : BehaviorSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val tradeRepo = ExposedTradeEventRepository(db)
    val positionRepo = ExposedPositionRepository(db)
    val transactional = ExposedTransactionalRunner(db)

    beforeEach {
        newSuspendedTransaction(db = db) {
            TradeEventsTable.deleteAll()
            PositionsTable.deleteAll()
        }
    }

    // Scenario 5: amend changes position
    given("a booked trade of 100 AAPL at \$150") {
        `when`("the trade is amended to 200 shares at \$160") {
            then("original is AMENDED, amend trade references original, and position is 200 shares at \$160 avg cost") {
                val publisher = mockk<TradeEventPublisher>(relaxed = true)
                val booking = TradeBookingService(tradeRepo, positionRepo, transactional, publisher)
                val lifecycle = TradeLifecycleService(tradeRepo, positionRepo, transactional, publisher)

                booking.handle(
                    BookTradeCommand(
                        tradeId = TradeId("t-amend-orig"),
                        portfolioId = PortfolioId("port-amend-1"),
                        instrumentId = InstrumentId("AAPL"),
                        assetClass = AssetClass.EQUITY,
                        side = Side.BUY,
                        quantity = BigDecimal("100"),
                        price = Money(BigDecimal("150.00"), USD),
                        tradedAt = TRADED_AT,
                    ),
                )
                lifecycle.handleAmend(
                    AmendTradeCommand(
                        originalTradeId = TradeId("t-amend-orig"),
                        newTradeId = TradeId("t-amend-new"),
                        portfolioId = PortfolioId("port-amend-1"),
                        instrumentId = InstrumentId("AAPL"),
                        assetClass = AssetClass.EQUITY,
                        side = Side.BUY,
                        quantity = BigDecimal("200"),
                        price = Money(BigDecimal("160.00"), USD),
                        tradedAt = TRADED_AT,
                    ),
                )

                val original = tradeRepo.findByTradeId(TradeId("t-amend-orig"))
                original.shouldNotBeNull()
                original.status shouldBe TradeStatus.AMENDED

                val amended = tradeRepo.findByTradeId(TradeId("t-amend-new"))
                amended.shouldNotBeNull()
                amended.type shouldBe TradeType.AMEND
                amended.originalTradeId shouldBe TradeId("t-amend-orig")

                val position = positionRepo.findByKey(PortfolioId("port-amend-1"), InstrumentId("AAPL"))
                position.shouldNotBeNull()
                position.quantity.compareTo(BigDecimal("200")) shouldBe 0
                position.averageCost.amount.compareTo(BigDecimal("160.00")) shouldBe 0
            }
        }
    }

    // Scenario 6: cancel reverses position to zero
    given("a booked trade of 100 AAPL at \$150 (for cancel)") {
        `when`("the trade is cancelled") {
            then("trade is CANCELLED and position quantity is zero") {
                val publisher = mockk<TradeEventPublisher>(relaxed = true)
                val booking = TradeBookingService(tradeRepo, positionRepo, transactional, publisher)
                val lifecycle = TradeLifecycleService(tradeRepo, positionRepo, transactional, publisher)

                booking.handle(
                    BookTradeCommand(
                        tradeId = TradeId("t-cancel-1"),
                        portfolioId = PortfolioId("port-cancel-1"),
                        instrumentId = InstrumentId("AAPL"),
                        assetClass = AssetClass.EQUITY,
                        side = Side.BUY,
                        quantity = BigDecimal("100"),
                        price = Money(BigDecimal("150.00"), USD),
                        tradedAt = TRADED_AT,
                    ),
                )
                lifecycle.handleCancel(
                    CancelTradeCommand(TradeId("t-cancel-1"), PortfolioId("port-cancel-1")),
                )

                val trade = tradeRepo.findByTradeId(TradeId("t-cancel-1"))
                trade.shouldNotBeNull()
                trade.status shouldBe TradeStatus.CANCELLED

                val position = positionRepo.findByKey(PortfolioId("port-cancel-1"), InstrumentId("AAPL"))
                position.shouldNotBeNull()
                position.quantity.compareTo(BigDecimal.ZERO) shouldBe 0
            }
        }
    }

    // Scenario 9: realized P&L through lifecycle
    given("a BUY position of 200 AAPL at \$100 average cost") {
        `when`("50 shares are SOLD at \$120") {
            then("realized P&L is \$1000 and remaining position is 150 shares") {
                val publisher = mockk<TradeEventPublisher>(relaxed = true)
                val booking = TradeBookingService(tradeRepo, positionRepo, transactional, publisher)

                booking.handle(
                    BookTradeCommand(
                        tradeId = TradeId("t-pnl-buy"),
                        portfolioId = PortfolioId("port-pnl-1"),
                        instrumentId = InstrumentId("AAPL"),
                        assetClass = AssetClass.EQUITY,
                        side = Side.BUY,
                        quantity = BigDecimal("200"),
                        price = Money(BigDecimal("100.00"), USD),
                        tradedAt = TRADED_AT,
                    ),
                )
                booking.handle(
                    BookTradeCommand(
                        tradeId = TradeId("t-pnl-sell"),
                        portfolioId = PortfolioId("port-pnl-1"),
                        instrumentId = InstrumentId("AAPL"),
                        assetClass = AssetClass.EQUITY,
                        side = Side.SELL,
                        quantity = BigDecimal("50"),
                        price = Money(BigDecimal("120.00"), USD),
                        tradedAt = TRADED_AT,
                    ),
                )

                val position = positionRepo.findByKey(PortfolioId("port-pnl-1"), InstrumentId("AAPL"))
                position.shouldNotBeNull()
                position.realizedPnl.amount.compareTo(BigDecimal("1000.00")) shouldBe 0
                position.quantity.compareTo(BigDecimal("150")) shouldBe 0
            }
        }
    }
})
