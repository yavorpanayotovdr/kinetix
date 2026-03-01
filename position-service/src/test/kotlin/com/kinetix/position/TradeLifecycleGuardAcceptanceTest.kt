package com.kinetix.position

import com.kinetix.common.model.*
import com.kinetix.position.kafka.TradeEventPublisher
import com.kinetix.position.persistence.DatabaseTestSetup
import com.kinetix.position.persistence.ExposedPositionRepository
import com.kinetix.position.persistence.ExposedTradeEventRepository
import com.kinetix.position.persistence.PositionsTable
import com.kinetix.position.persistence.TradeEventsTable
import com.kinetix.position.service.AmendTradeCommand
import com.kinetix.position.service.BookTradeCommand
import com.kinetix.position.service.CancelTradeCommand
import com.kinetix.position.service.ExposedTransactionalRunner
import com.kinetix.position.service.InvalidTradeStateException
import com.kinetix.position.service.TradeBookingService
import com.kinetix.position.service.TradeLifecycleService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

class TradeLifecycleGuardAcceptanceTest : FunSpec({

    val usd = Currency.getInstance("USD")
    val tradedAt = Instant.parse("2025-01-15T10:00:00Z")
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

    test("rejects cancel on already-cancelled trade with InvalidTradeStateException") {
        val publisher = mockk<TradeEventPublisher>(relaxed = true)
        val booking = TradeBookingService(tradeRepo, positionRepo, transactional, publisher)
        val lifecycle = TradeLifecycleService(tradeRepo, positionRepo, transactional, publisher)

        booking.handle(
            BookTradeCommand(
                tradeId = TradeId("t-cancel-twice"),
                portfolioId = PortfolioId("port-cancel-2"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("100"),
                price = Money(BigDecimal("150.00"), usd),
                tradedAt = tradedAt,
            ),
        )
        lifecycle.handleCancel(
            CancelTradeCommand(TradeId("t-cancel-twice"), PortfolioId("port-cancel-2")),
        )

        val ex = shouldThrow<InvalidTradeStateException> {
            lifecycle.handleCancel(
                CancelTradeCommand(TradeId("t-cancel-twice"), PortfolioId("port-cancel-2")),
            )
        }
        ex.currentStatus shouldBe TradeStatus.CANCELLED
        ex.attemptedAction shouldBe "cancel"
    }

    test("rejects amend on cancelled trade with InvalidTradeStateException") {
        val publisher = mockk<TradeEventPublisher>(relaxed = true)
        val booking = TradeBookingService(tradeRepo, positionRepo, transactional, publisher)
        val lifecycle = TradeLifecycleService(tradeRepo, positionRepo, transactional, publisher)

        booking.handle(
            BookTradeCommand(
                tradeId = TradeId("t-amend-cancelled"),
                portfolioId = PortfolioId("port-amend-cancel-1"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("100"),
                price = Money(BigDecimal("150.00"), usd),
                tradedAt = tradedAt,
            ),
        )
        lifecycle.handleCancel(
            CancelTradeCommand(TradeId("t-amend-cancelled"), PortfolioId("port-amend-cancel-1")),
        )

        val ex = shouldThrow<InvalidTradeStateException> {
            lifecycle.handleAmend(
                AmendTradeCommand(
                    originalTradeId = TradeId("t-amend-cancelled"),
                    newTradeId = TradeId("t-amend-cancelled-new"),
                    portfolioId = PortfolioId("port-amend-cancel-1"),
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    side = Side.BUY,
                    quantity = BigDecimal("200"),
                    price = Money(BigDecimal("160.00"), usd),
                    tradedAt = tradedAt,
                ),
            )
        }
        ex.currentStatus shouldBe TradeStatus.CANCELLED
        ex.attemptedAction shouldBe "amend"
    }
})
