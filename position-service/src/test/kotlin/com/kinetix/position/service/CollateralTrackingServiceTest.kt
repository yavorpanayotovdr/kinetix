package com.kinetix.position.service

import com.kinetix.position.model.CollateralBalance
import com.kinetix.position.model.CollateralDirection
import com.kinetix.position.model.CollateralType
import com.kinetix.position.persistence.CollateralBalanceRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private infix fun BigDecimal.shouldBeEqualTo(expected: BigDecimal) {
    (this.compareTo(expected) == 0) shouldBe true
}

private fun balance(
    id: Long? = 1L,
    counterpartyId: String = "CP-001",
    nettingSetId: String? = null,
    collateralType: CollateralType = CollateralType.CASH,
    amount: BigDecimal = BigDecimal("1000000"),
    currency: String = "USD",
    direction: CollateralDirection = CollateralDirection.RECEIVED,
    asOfDate: LocalDate = LocalDate.of(2024, 1, 15),
    valueAfterHaircut: BigDecimal = amount * (BigDecimal.ONE - collateralType.haircut),
) = CollateralBalance(
    id = id,
    counterpartyId = counterpartyId,
    nettingSetId = nettingSetId,
    collateralType = collateralType,
    amount = amount,
    currency = currency,
    direction = direction,
    asOfDate = asOfDate,
    valueAfterHaircut = valueAfterHaircut,
    createdAt = Instant.parse("2024-01-15T10:00:00Z"),
    updatedAt = Instant.parse("2024-01-15T10:00:00Z"),
)

class CollateralTrackingServiceTest : FunSpec({

    val repository = mockk<CollateralBalanceRepository>()
    val service = CollateralTrackingService(repository)

    context("postCollateral") {

        test("applies zero haircut to CASH collateral") {
            val capturedBalance = slot<CollateralBalance>()
            coEvery { repository.save(capture(capturedBalance)) } answers {
                capturedBalance.captured.copy(id = 1L)
            }

            service.postCollateral(
                counterpartyId = "CP-001",
                nettingSetId = null,
                collateralType = CollateralType.CASH,
                amount = BigDecimal("1000000"),
                currency = "USD",
                direction = CollateralDirection.RECEIVED,
            )

            capturedBalance.captured.valueAfterHaircut shouldBeEqualTo BigDecimal("1000000")
        }

        test("applies 3% haircut to GOVERNMENT_BOND collateral") {
            val capturedBalance = slot<CollateralBalance>()
            coEvery { repository.save(capture(capturedBalance)) } answers {
                capturedBalance.captured.copy(id = 1L)
            }

            service.postCollateral(
                counterpartyId = "CP-001",
                nettingSetId = null,
                collateralType = CollateralType.GOVERNMENT_BOND,
                amount = BigDecimal("1000000"),
                currency = "USD",
                direction = CollateralDirection.RECEIVED,
            )

            // 1,000,000 * (1 - 0.03) = 970,000
            capturedBalance.captured.valueAfterHaircut shouldBeEqualTo BigDecimal("970000")
        }

        test("applies 10% haircut to CORPORATE_BOND collateral") {
            val capturedBalance = slot<CollateralBalance>()
            coEvery { repository.save(capture(capturedBalance)) } answers {
                capturedBalance.captured.copy(id = 1L)
            }

            service.postCollateral(
                counterpartyId = "CP-001",
                nettingSetId = null,
                collateralType = CollateralType.CORPORATE_BOND,
                amount = BigDecimal("500000"),
                currency = "USD",
                direction = CollateralPosted,
            )

            // 500,000 * (1 - 0.10) = 450,000
            capturedBalance.captured.valueAfterHaircut shouldBeEqualTo BigDecimal("450000")
        }

        test("applies 20% haircut to EQUITY collateral") {
            val capturedBalance = slot<CollateralBalance>()
            coEvery { repository.save(capture(capturedBalance)) } answers {
                capturedBalance.captured.copy(id = 1L)
            }

            service.postCollateral(
                counterpartyId = "CP-001",
                nettingSetId = null,
                collateralType = CollateralType.EQUITY,
                amount = BigDecimal("250000"),
                currency = "USD",
                direction = CollateralDirection.POSTED,
            )

            // 250,000 * (1 - 0.20) = 200,000
            capturedBalance.captured.valueAfterHaircut shouldBeEqualTo BigDecimal("200000")
        }

        test("rejects zero amount") {
            shouldThrow<IllegalArgumentException> {
                service.postCollateral(
                    counterpartyId = "CP-001",
                    nettingSetId = null,
                    collateralType = CollateralType.CASH,
                    amount = BigDecimal.ZERO,
                    currency = "USD",
                    direction = CollateralDirection.RECEIVED,
                )
            }
        }

        test("rejects negative amount") {
            shouldThrow<IllegalArgumentException> {
                service.postCollateral(
                    counterpartyId = "CP-001",
                    nettingSetId = null,
                    collateralType = CollateralType.CASH,
                    amount = BigDecimal("-100"),
                    currency = "USD",
                    direction = CollateralDirection.RECEIVED,
                )
            }
        }

        test("persists netting set ID") {
            val capturedBalance = slot<CollateralBalance>()
            coEvery { repository.save(capture(capturedBalance)) } answers {
                capturedBalance.captured.copy(id = 1L)
            }

            service.postCollateral(
                counterpartyId = "CP-001",
                nettingSetId = "NS-001",
                collateralType = CollateralType.CASH,
                amount = BigDecimal("1000000"),
                currency = "USD",
                direction = CollateralDirection.RECEIVED,
            )

            capturedBalance.captured.nettingSetId shouldBe "NS-001"
        }

        test("uses provided asOfDate") {
            val capturedBalance = slot<CollateralBalance>()
            coEvery { repository.save(capture(capturedBalance)) } answers {
                capturedBalance.captured.copy(id = 1L)
            }
            val date = LocalDate.of(2024, 6, 30)

            service.postCollateral(
                counterpartyId = "CP-001",
                nettingSetId = null,
                collateralType = CollateralType.CASH,
                amount = BigDecimal("1000000"),
                currency = "USD",
                direction = CollateralDirection.RECEIVED,
                asOfDate = date,
            )

            capturedBalance.captured.asOfDate shouldBe date
        }

        test("returns saved balance with assigned ID") {
            coEvery { repository.save(any()) } answers {
                (args[0] as CollateralBalance).copy(id = 42L)
            }

            val result = service.postCollateral(
                counterpartyId = "CP-001",
                nettingSetId = null,
                collateralType = CollateralType.CASH,
                amount = BigDecimal("1000000"),
                currency = "USD",
                direction = CollateralDirection.RECEIVED,
            )

            result.id shouldBe 42L
        }
    }

    context("getCollateralForCounterparty") {

        test("delegates to repository") {
            val expectedBalances = listOf(
                balance(id = 1L, counterpartyId = "CP-001", direction = CollateralDirection.RECEIVED),
                balance(id = 2L, counterpartyId = "CP-001", direction = CollateralDirection.POSTED),
            )
            coEvery { repository.findByCounterpartyId("CP-001") } returns expectedBalances

            val result = service.getCollateralForCounterparty("CP-001")

            result shouldBe expectedBalances
            coVerify(exactly = 1) { repository.findByCounterpartyId("CP-001") }
        }

        test("returns empty list when no collateral posted") {
            coEvery { repository.findByCounterpartyId("CP-999") } returns emptyList()

            val result = service.getCollateralForCounterparty("CP-999")

            result shouldBe emptyList()
        }
    }

    context("getCollateralForNettingSet") {

        test("delegates to repository") {
            val expectedBalances = listOf(
                balance(id = 1L, nettingSetId = "NS-001"),
            )
            coEvery { repository.findByNettingSetId("NS-001") } returns expectedBalances

            val result = service.getCollateralForNettingSet("NS-001")

            result shouldBe expectedBalances
        }
    }

    context("netCollateral") {

        test("separates received from posted collateral by post-haircut value") {
            coEvery { repository.findByCounterpartyId("CP-001") } returns listOf(
                balance(
                    id = 1L,
                    direction = CollateralDirection.RECEIVED,
                    collateralType = CollateralType.CASH,
                    amount = BigDecimal("1000000"),
                    valueAfterHaircut = BigDecimal("1000000"),
                ),
                balance(
                    id = 2L,
                    direction = CollateralDirection.POSTED,
                    collateralType = CollateralType.GOVERNMENT_BOND,
                    amount = BigDecimal("500000"),
                    valueAfterHaircut = BigDecimal("485000"),
                ),
            )

            val (received, posted) = service.netCollateral("CP-001")

            received shouldBe BigDecimal("1000000")
            posted shouldBe BigDecimal("485000")
        }

        test("sums multiple received balances") {
            coEvery { repository.findByCounterpartyId("CP-001") } returns listOf(
                balance(id = 1L, direction = CollateralDirection.RECEIVED, valueAfterHaircut = BigDecimal("300000")),
                balance(id = 2L, direction = CollateralDirection.RECEIVED, valueAfterHaircut = BigDecimal("200000")),
            )

            val (received, posted) = service.netCollateral("CP-001")

            received shouldBe BigDecimal("500000")
            posted shouldBe BigDecimal.ZERO
        }

        test("returns zero for both when no collateral") {
            coEvery { repository.findByCounterpartyId("CP-001") } returns emptyList()

            val (received, posted) = service.netCollateral("CP-001")

            received shouldBe BigDecimal.ZERO
            posted shouldBe BigDecimal.ZERO
        }

        test("net_net_exposure formula: net_exposure - received + posted reduces exposure") {
            // A counterparty posts 2M cash. The net-net exposure should be lower than gross.
            coEvery { repository.findByCounterpartyId("CP-001") } returns listOf(
                balance(
                    id = 1L,
                    direction = CollateralDirection.RECEIVED,
                    collateralType = CollateralType.CASH,
                    amount = BigDecimal("2000000"),
                    valueAfterHaircut = BigDecimal("2000000"),
                ),
            )

            val (received, posted) = service.netCollateral("CP-001")

            // net_net_exposure = net_exposure - received + posted
            // With net_exposure = 5M: 5M - 2M + 0 = 3M (reduced)
            val netExposure = BigDecimal("5000000")
            val netNetExposure = netExposure - received + posted
            netNetExposure shouldBe BigDecimal("3000000")
        }
    }
})

private val CollateralPosted = CollateralDirection.POSTED
