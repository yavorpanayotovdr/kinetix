package com.kinetix.risk.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.BookId
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.Position
import com.kinetix.risk.model.DailyRiskSnapshot
import com.kinetix.risk.model.IntradayPnlSnapshot
import com.kinetix.risk.model.PnlTrigger
import com.kinetix.risk.model.SodBaseline
import com.kinetix.risk.model.SnapshotType
import com.kinetix.risk.persistence.DailyRiskSnapshotRepository
import com.kinetix.risk.persistence.IntradayPnlRepository
import com.kinetix.risk.persistence.SodBaselineRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

private val USD = Currency.getInstance("USD")
private val BOOK = BookId("book-1")
private val TODAY = LocalDate.now()
private fun bd(v: String) = BigDecimal(v)

private fun position(
    instrumentId: String,
    quantity: String,
    avgCost: String,
    marketPrice: String,
    realizedPnl: String = "0.00",
): Position = Position(
    bookId = BOOK,
    instrumentId = InstrumentId(instrumentId),
    assetClass = AssetClass.EQUITY,
    quantity = bd(quantity),
    averageCost = Money(bd(avgCost), USD),
    marketPrice = Money(bd(marketPrice), USD),
    realizedPnl = Money(bd(realizedPnl), USD),
)

private fun sodSnapshot(
    instrumentId: String,
    quantity: String = "100",
    marketPrice: String = "50.00",
    delta: Double? = null,
    gamma: Double? = null,
    vega: Double? = null,
    theta: Double? = null,
    rho: Double? = null,
): DailyRiskSnapshot = DailyRiskSnapshot(
    bookId = BOOK,
    snapshotDate = TODAY,
    instrumentId = InstrumentId(instrumentId),
    assetClass = AssetClass.EQUITY,
    quantity = bd(quantity),
    marketPrice = bd(marketPrice),
    delta = delta,
    gamma = gamma,
    vega = vega,
    theta = theta,
    rho = rho,
)

private fun sodBaseline(bookId: BookId = BOOK, date: LocalDate = TODAY): SodBaseline = SodBaseline(
    bookId = bookId,
    baselineDate = date,
    snapshotType = SnapshotType.AUTO,
    createdAt = Instant.now(),
    sourceJobId = UUID.randomUUID(),
    calculationType = "PARAMETRIC",
    varValue = null,
    expectedShortfall = null,
)

private fun previousSnapshot(
    hwm: BigDecimal,
    totalPnl: BigDecimal = hwm,
    secondsAgo: Long = 10,
): IntradayPnlSnapshot = IntradayPnlSnapshot(
    bookId = BOOK,
    snapshotAt = Instant.now().minusSeconds(secondsAgo),
    baseCurrency = "USD",
    trigger = PnlTrigger.POSITION_CHANGE,
    totalPnl = totalPnl,
    realisedPnl = BigDecimal.ZERO,
    unrealisedPnl = totalPnl,
    deltaPnl = BigDecimal.ZERO,
    gammaPnl = BigDecimal.ZERO,
    vegaPnl = BigDecimal.ZERO,
    thetaPnl = BigDecimal.ZERO,
    rhoPnl = BigDecimal.ZERO,
    unexplainedPnl = totalPnl,
    highWaterMark = hwm,
)

/**
 * Fresh mocks for each test. Each test is fully isolated — no shared state between calls.
 */
private class TestFixtures {
    val sodBaselineRepo = mockk<SodBaselineRepository>()
    val dailyRiskSnapshotRepo = mockk<DailyRiskSnapshotRepository>()
    val pnlRepository = mockk<IntradayPnlRepository>(relaxed = true)
    val positionProvider = mockk<com.kinetix.risk.client.PositionProvider>()
    val publisher = mockk<IntradayPnlPublisher>(relaxed = true)

    val service = IntradayPnlService(
        sodBaselineRepository = sodBaselineRepo,
        dailyRiskSnapshotRepository = dailyRiskSnapshotRepo,
        intradayPnlRepository = pnlRepository,
        positionProvider = positionProvider,
        pnlAttributionService = PnlAttributionService(),
        publisher = publisher,
    )
}

class IntradayPnlServiceTest : FunSpec({

    test("skips recomputation and returns null when no SOD baseline exists") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns null

        val result = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        result.shouldBeNull()
        coVerify(exactly = 0) { f.pnlRepository.save(any()) }
        coVerify(exactly = 0) { f.publisher.publish(any()) }
    }

    test("computes total P&L from position state and persists snapshot") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL", quantity = "100", marketPrice = "100.00"),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "110.00", realizedPnl = "0.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns null

        val snapshot = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        snapshot.shouldNotBeNull()
        // unrealised = (110 - 90) * 100 = 2000, realised = 0
        snapshot.totalPnl.compareTo(bd("2000.00")) shouldBe 0
        snapshot.realisedPnl.compareTo(bd("0.00")) shouldBe 0
        snapshot.unrealisedPnl.compareTo(bd("2000.00")) shouldBe 0
        coVerify(exactly = 1) { f.pnlRepository.save(any()) }
        coVerify(exactly = 1) { f.publisher.publish(any()) }
    }

    test("total P&L includes realised P&L from position") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL", quantity = "100", marketPrice = "100.00"),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            position("AAPL", quantity = "50", avgCost = "90.00", marketPrice = "110.00", realizedPnl = "500.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns null

        val snapshot = f.service.recompute(BOOK, PnlTrigger.TRADE_BOOKED, correlationId = null)

        snapshot.shouldNotBeNull()
        // unrealised = (110 - 90) * 50 = 1000, realised = 500, total = 1500
        snapshot.realisedPnl.compareTo(bd("500.00")) shouldBe 0
        snapshot.unrealisedPnl.compareTo(bd("1000.00")) shouldBe 0
        snapshot.totalPnl.compareTo(bd("1500.00")) shouldBe 0
    }

    test("high-water mark is initialised to total P&L when no prior snapshot exists") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL", quantity = "100", marketPrice = "100.00"),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "110.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns null

        val snapshot = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        snapshot.shouldNotBeNull()
        snapshot.highWaterMark.compareTo(snapshot.totalPnl) shouldBe 0
    }

    test("high-water mark is non-decreasing: previous HWM exceeds current P&L") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL", quantity = "100", marketPrice = "100.00"),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            // Price fell: unrealised = (95 - 90) * 100 = 500
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "95.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns previousSnapshot(hwm = bd("3000.00"), totalPnl = bd("2000.00"))

        val snapshot = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        snapshot.shouldNotBeNull()
        snapshot.totalPnl.compareTo(bd("500.00")) shouldBe 0
        // HWM must not fall below previous HWM of 3000
        snapshot.highWaterMark.compareTo(bd("3000.00")) shouldBe 0
    }

    test("high-water mark advances when new total P&L exceeds previous HWM") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL", quantity = "100", marketPrice = "100.00"),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            // Price rose: unrealised = (120 - 90) * 100 = 3000
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "120.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns previousSnapshot(hwm = bd("1000.00"))

        val snapshot = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        snapshot.shouldNotBeNull()
        snapshot.totalPnl.compareTo(bd("3000.00")) shouldBe 0
        snapshot.highWaterMark.compareTo(bd("3000.00")) shouldBe 0
    }

    test("attribution sums to total: unexplained absorbs residual") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot(
                "AAPL", quantity = "100", marketPrice = "100.00",
                delta = 0.8, gamma = 0.05, vega = 0.0, theta = -0.1, rho = 0.0,
            ),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "110.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns null

        val snapshot = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        snapshot.shouldNotBeNull()
        val sumOfGreeks = snapshot.deltaPnl + snapshot.gammaPnl + snapshot.vegaPnl +
            snapshot.thetaPnl + snapshot.rhoPnl + snapshot.unexplainedPnl
        // Attribution invariant: sum of components == total
        sumOfGreeks.compareTo(snapshot.totalPnl) shouldBe 0
    }

    test("respects debounce: skips snapshot within debounce interval") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.pnlRepository.findLatest(BOOK) } returns previousSnapshot(
            hwm = bd("1000.00"),
            secondsAgo = 0, // less than 1s ago
        )

        val result = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        result.shouldBeNull()
        coVerify(exactly = 0) { f.pnlRepository.save(any()) }
        coVerify(exactly = 0) { f.publisher.publish(any()) }
    }

    test("processes snapshot after debounce interval has elapsed") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL"),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "110.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns previousSnapshot(
            hwm = bd("1000.00"),
            secondsAgo = 2, // more than 1s ago
        )

        val result = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)
        result.shouldNotBeNull()
    }

    test("persists snapshot with the provided correlation ID") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL"),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "110.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns null

        val captured = slot<IntradayPnlSnapshot>()
        coEvery { f.pnlRepository.save(capture(captured)) } returns Unit

        f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = "corr-xyz")

        captured.captured.correlationId shouldBe "corr-xyz"
    }

    test("snapshot contains per-instrument breakdown for each position with a SOD snapshot") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL", quantity = "100", marketPrice = "100.00", delta = 0.9),
            sodSnapshot("MSFT", quantity = "50", marketPrice = "200.00", delta = 0.8),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "110.00"),
            position("MSFT", quantity = "50", avgCost = "190.00", marketPrice = "210.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns null

        val snapshot = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        snapshot.shouldNotBeNull()
        snapshot.instrumentPnl.size shouldBe 2
        val byId = snapshot.instrumentPnl.associateBy { it.instrumentId }
        byId["AAPL"].shouldNotBeNull()
        byId["MSFT"].shouldNotBeNull()
        // AAPL: unrealised = (110-90)*100 = 2000
        byId["AAPL"]!!.totalPnl.toBigDecimal().compareTo(bd("2000")) shouldBe 0
        // MSFT: unrealised = (210-190)*50 = 1000
        byId["MSFT"]!!.totalPnl.toBigDecimal().compareTo(bd("1000")) shouldBe 0
    }

    test("per-instrument breakdown sums to portfolio total") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL", quantity = "100", marketPrice = "100.00", delta = 0.8, gamma = 0.05),
            sodSnapshot("MSFT", quantity = "50", marketPrice = "200.00", delta = 0.7),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "110.00"),
            position("MSFT", quantity = "50", avgCost = "190.00", marketPrice = "210.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns null

        val snapshot = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        snapshot.shouldNotBeNull()
        val sumOfInstrumentTotals = snapshot.instrumentPnl
            .sumOf { it.totalPnl.toBigDecimal() }
        sumOfInstrumentTotals.compareTo(snapshot.totalPnl) shouldBe 0
    }

    test("position with no SOD snapshot is excluded from per-instrument breakdown") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        // Only AAPL has a SOD snapshot; MSFT does not
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL", quantity = "100", marketPrice = "100.00"),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "110.00"),
            position("MSFT", quantity = "50", avgCost = "190.00", marketPrice = "210.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns null

        val snapshot = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        snapshot.shouldNotBeNull()
        snapshot.instrumentPnl.size shouldBe 1
        snapshot.instrumentPnl[0].instrumentId shouldBe "AAPL"
    }

    test("aggregates P&L across multiple positions in the book") {
        val f = TestFixtures()
        coEvery { f.sodBaselineRepo.findByBookIdAndDate(BOOK, any()) } returns sodBaseline()
        coEvery { f.dailyRiskSnapshotRepo.findByBookIdAndDate(BOOK, TODAY) } returns listOf(
            sodSnapshot("AAPL", quantity = "100", marketPrice = "100.00"),
            sodSnapshot("MSFT", quantity = "50", marketPrice = "200.00"),
        )
        coEvery { f.positionProvider.getPositions(BOOK) } returns listOf(
            // AAPL unrealised = (110 - 90) * 100 = 2000
            position("AAPL", quantity = "100", avgCost = "90.00", marketPrice = "110.00"),
            // MSFT unrealised = (210 - 190) * 50 = 1000
            position("MSFT", quantity = "50", avgCost = "190.00", marketPrice = "210.00"),
        )
        coEvery { f.pnlRepository.findLatest(BOOK) } returns null

        val snapshot = f.service.recompute(BOOK, PnlTrigger.POSITION_CHANGE, correlationId = null)

        snapshot.shouldNotBeNull()
        snapshot.totalPnl.compareTo(bd("3000.00")) shouldBe 0
    }
})
