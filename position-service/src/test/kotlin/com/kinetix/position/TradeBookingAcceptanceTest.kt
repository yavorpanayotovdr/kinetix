package com.kinetix.position

import com.kinetix.common.model.*
import com.kinetix.position.kafka.TradeEventPublisher
import com.kinetix.position.model.LimitBreachSeverity
import com.kinetix.position.model.TradeLimits
import com.kinetix.position.persistence.DatabaseTestSetup
import com.kinetix.position.persistence.ExposedPositionRepository
import com.kinetix.position.persistence.ExposedTradeEventRepository
import com.kinetix.position.persistence.PositionsTable
import com.kinetix.position.persistence.TradeEventsTable
import com.kinetix.position.service.BookTradeCommand
import com.kinetix.position.service.ExposedTransactionalRunner
import com.kinetix.position.service.LimitBreachException
import com.kinetix.position.service.LimitCheckService
import com.kinetix.position.service.TradeBookingService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val TRADED_AT = Instant.parse("2025-01-15T10:00:00Z")

class TradeBookingAcceptanceTest : BehaviorSpec({

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

    // Scenario 1: book a trade creates position
    given("a portfolio with no existing positions") {
        `when`("a BUY trade is submitted for 100 AAPL at \$150") {
            then("trade is persisted, position is created with qty=100 and avgCost=\$150, and publisher is called once") {
                val publisher = mockk<TradeEventPublisher>(relaxed = true)
                val service = TradeBookingService(tradeRepo, positionRepo, transactional, publisher)

                val result = service.handle(
                    BookTradeCommand(
                        tradeId = TradeId("t-book-1"),
                        portfolioId = PortfolioId("port-book-1"),
                        instrumentId = InstrumentId("AAPL"),
                        assetClass = AssetClass.EQUITY,
                        side = Side.BUY,
                        quantity = BigDecimal("100"),
                        price = Money(BigDecimal("150.00"), USD),
                        tradedAt = TRADED_AT,
                    ),
                )

                val saved = tradeRepo.findByTradeId(TradeId("t-book-1"))
                saved?.tradeId shouldBe TradeId("t-book-1")
                saved?.side shouldBe Side.BUY
                saved?.quantity?.compareTo(BigDecimal("100")) shouldBe 0

                val position = positionRepo.findByKey(PortfolioId("port-book-1"), InstrumentId("AAPL"))
                position?.quantity?.compareTo(BigDecimal("100")) shouldBe 0
                position?.averageCost?.amount?.compareTo(BigDecimal("150.00")) shouldBe 0

                coVerify(exactly = 1) { publisher.publish(any()) }
                result.trade.tradeId shouldBe TradeId("t-book-1")
                result.warnings.shouldBeEmpty()
            }
        }
    }

    // Scenario 2: hard limit breach blocks trade
    given("a hard position limit of 1000 shares") {
        `when`("a BUY trade for 1001 shares is submitted") {
            then("LimitBreachException is thrown, no trade or position is created, and publisher is never called") {
                val publisher = mockk<TradeEventPublisher>(relaxed = true)
                val limits = TradeLimits(positionLimit = BigDecimal("1000"))
                val limitCheck = LimitCheckService(positionRepo, limits)
                val service = TradeBookingService(tradeRepo, positionRepo, transactional, publisher, limitCheck)

                var caughtException: Exception? = null
                try {
                    service.handle(
                        BookTradeCommand(
                            tradeId = TradeId("t-hard-1"),
                            portfolioId = PortfolioId("port-hard-1"),
                            instrumentId = InstrumentId("AAPL"),
                            assetClass = AssetClass.EQUITY,
                            side = Side.BUY,
                            quantity = BigDecimal("1001"),
                            price = Money(BigDecimal("100.00"), USD),
                            tradedAt = TRADED_AT,
                        ),
                    )
                } catch (e: LimitBreachException) {
                    caughtException = e
                }
                (caughtException is LimitBreachException) shouldBe true

                tradeRepo.findByTradeId(TradeId("t-hard-1")) shouldBe null
                positionRepo.findByKey(PortfolioId("port-hard-1"), InstrumentId("AAPL")) shouldBe null
                coVerify(exactly = 0) { publisher.publish(any()) }
            }
        }
    }

    // Scenario 3: soft limit warning passes trade with warning
    given("a soft position limit of 1000 shares at 80% threshold with an existing position of 800 shares") {
        `when`("a BUY trade for 1 share is submitted (total would be 801, above 80% soft threshold)") {
            then("trade succeeds with a SOFT severity warning and position is updated to 801 shares") {
                val publisher = mockk<TradeEventPublisher>(relaxed = true)
                // Seed 800 shares without limit check
                TradeBookingService(tradeRepo, positionRepo, transactional, publisher).handle(
                    BookTradeCommand(
                        tradeId = TradeId("t-soft-seed"),
                        portfolioId = PortfolioId("port-soft-1"),
                        instrumentId = InstrumentId("AAPL"),
                        assetClass = AssetClass.EQUITY,
                        side = Side.BUY,
                        quantity = BigDecimal("800"),
                        price = Money(BigDecimal("100.00"), USD),
                        tradedAt = TRADED_AT,
                    ),
                )

                val limits = TradeLimits(positionLimit = BigDecimal("1000"), softLimitPct = 0.8)
                val limitCheck = LimitCheckService(positionRepo, limits)
                val service = TradeBookingService(tradeRepo, positionRepo, transactional, publisher, limitCheck)

                val result = service.handle(
                    BookTradeCommand(
                        tradeId = TradeId("t-soft-1"),
                        portfolioId = PortfolioId("port-soft-1"),
                        instrumentId = InstrumentId("AAPL"),
                        assetClass = AssetClass.EQUITY,
                        side = Side.BUY,
                        quantity = BigDecimal("1"),
                        price = Money(BigDecimal("100.00"), USD),
                        tradedAt = TRADED_AT,
                    ),
                )

                tradeRepo.findByTradeId(TradeId("t-soft-1"))?.tradeId shouldBe TradeId("t-soft-1")
                result.warnings shouldHaveSize 1
                result.warnings[0].severity shouldBe LimitBreachSeverity.SOFT

                val position = positionRepo.findByKey(PortfolioId("port-soft-1"), InstrumentId("AAPL"))
                position?.quantity?.compareTo(BigDecimal("801")) shouldBe 0
            }
        }
    }

    // Scenario 4: duplicate trade ID handled idempotently
    given("a previously booked trade with ID t-idem-1") {
        `when`("the same trade ID is submitted a second time") {
            then("only one trade record exists, publisher called once, and position quantity is 100") {
                val publisher = mockk<TradeEventPublisher>(relaxed = true)
                val service = TradeBookingService(tradeRepo, positionRepo, transactional, publisher)

                val command = BookTradeCommand(
                    tradeId = TradeId("t-idem-1"),
                    portfolioId = PortfolioId("port-idem-1"),
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    side = Side.BUY,
                    quantity = BigDecimal("100"),
                    price = Money(BigDecimal("150.00"), USD),
                    tradedAt = TRADED_AT,
                )
                service.handle(command)
                service.handle(command)

                tradeRepo.findByPortfolioId(PortfolioId("port-idem-1")) shouldHaveSize 1
                coVerify(exactly = 1) { publisher.publish(any()) }

                val position = positionRepo.findByKey(PortfolioId("port-idem-1"), InstrumentId("AAPL"))
                position?.quantity?.compareTo(BigDecimal("100")) shouldBe 0
            }
        }
    }
})
