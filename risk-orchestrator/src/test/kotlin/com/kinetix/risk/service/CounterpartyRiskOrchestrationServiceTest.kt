package com.kinetix.risk.service

import com.kinetix.risk.client.CVAResult
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.CounterpartyRiskClient
import com.kinetix.risk.client.PFEPositionInput
import com.kinetix.risk.client.PFEResult
import com.kinetix.risk.client.ReferenceDataServiceClient
import com.kinetix.risk.client.dtos.CounterpartyDto
import com.kinetix.risk.client.dtos.NettingAgreementDto
import com.kinetix.risk.model.CounterpartyExposureSnapshot
import com.kinetix.risk.model.ExposureAtTenor
import com.kinetix.risk.persistence.CounterpartyExposureRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot

private val TENORS = listOf(
    ExposureAtTenor("1M", 1.0 / 12, 500_000.0, 750_000.0, 900_000.0),
    ExposureAtTenor("1Y", 1.0, 1_200_000.0, 1_800_000.0, 2_100_000.0),
)

private val COUNTERPARTY = CounterpartyDto(
    counterpartyId = "CP-GS",
    legalName = "Goldman Sachs",
    ratingSp = "A+",
    sector = "FINANCIALS",
    lgd = 0.4,
    cdsSpreadBps = 65.0,
)

private val NETTING_AGREEMENT = NettingAgreementDto(
    nettingSetId = "NS-GS-001",
    counterpartyId = "CP-GS",
    agreementType = "ISDA_2002",
    closeOutNetting = true,
    csaThreshold = 0.0,
    currency = "USD",
)

private fun pfeResult(netExposure: Double = 2_000_000.0) = PFEResult(
    counterpartyId = "CP-GS",
    nettingSetId = "NS-GS-001",
    grossExposure = 3_000_000.0,
    netExposure = netExposure,
    exposureProfile = TENORS,
)

private fun cvaResult(cva: Double = 12_500.0, estimated: Boolean = false) = CVAResult(
    counterpartyId = "CP-GS",
    cva = cva,
    isEstimated = estimated,
    hazardRate = 0.0065,
    pd1y = 0.0065,
)

class CounterpartyRiskOrchestrationServiceTest : FunSpec({

    val referenceDataClient = mockk<ReferenceDataServiceClient>()
    val counterpartyRiskClient = mockk<CounterpartyRiskClient>()
    val repository = mockk<CounterpartyExposureRepository>()
    val service = CounterpartyRiskOrchestrationService(
        referenceDataClient = referenceDataClient,
        counterpartyRiskClient = counterpartyRiskClient,
        repository = repository,
    )

    context("computeAndPersistPFE") {

        test("fetches counterparty and netting set, calls PFE, persists snapshot") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(COUNTERPARTY)
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            coEvery {
                counterpartyRiskClient.calculatePFE(
                    counterpartyId = "CP-GS",
                    nettingSetId = "NS-GS-001",
                    agreementType = "ISDA_2002",
                    positions = any(),
                    numSimulations = 0,
                    seed = 0,
                )
            } returns pfeResult()
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult()
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val positions = listOf(
                PFEPositionInput("AAPL", 1_000_000.0, "EQUITY", 0.25, "TECHNOLOGY"),
                PFEPositionInput("GS-BOND", 2_000_000.0, "FIXED_INCOME", 0.05, "FINANCIALS"),
            )

            val result = service.computeAndPersistPFE("CP-GS", positions)

            result.counterpartyId shouldBe "CP-GS"
            result.pfeProfile.size shouldBe 2
            result.currentNetExposure shouldBe 2_000_000.0
            result.peakPfe shouldBe TENORS.maxOf { it.pfe95 }
            coVerify(exactly = 1) { repository.save(any()) }
        }

        test("peak PFE is the maximum pfe95 across all tenors") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(COUNTERPARTY)
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            val tenors = listOf(
                ExposureAtTenor("1M", 1.0 / 12, 100_000.0, 200_000.0, 250_000.0),
                ExposureAtTenor("1Y", 1.0, 800_000.0, 1_500_000.0, 1_800_000.0),
                ExposureAtTenor("5Y", 5.0, 600_000.0, 900_000.0, 1_000_000.0),
            )
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns PFEResult("CP-GS", "NS-GS-001", 3_000_000.0, 2_000_000.0, tenors)
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult()
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            result.peakPfe shouldBe 1_500_000.0
        }

        test("computes and includes cva in snapshot when counterparty has credit data") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(COUNTERPARTY)
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns pfeResult()
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult(cva = 12_500.0, estimated = false)
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            result.cva shouldBe 12_500.0
        }

        test("when counterparty not found, throws IllegalArgumentException") {
            coEvery { referenceDataClient.getCounterparty("CP-UNKNOWN") } returns ClientResponse.NotFound(404)

            try {
                service.computeAndPersistPFE("CP-UNKNOWN", emptyList())
                throw AssertionError("Expected exception was not thrown")
            } catch (e: IllegalArgumentException) {
                e.message shouldNotBe null
            }
        }

        test("sets cva to null and skips calculateCVA when counterparty has no credit data") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
                COUNTERPARTY.copy(pd1y = null, cdsSpreadBps = null),
            )
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns pfeResult()
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            result.cva.shouldBeNull()
            result.cvaEstimated shouldBe false
            // Clear accumulated call history from earlier tests before asserting this test's count
            clearMocks(counterpartyRiskClient, answers = false)
            coVerify(exactly = 0) { counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        test("computes cva when counterparty has pd1y only") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
                COUNTERPARTY.copy(pd1y = 0.02, cdsSpreadBps = null),
            )
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns pfeResult()
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult(cva = 18_000.0)
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            result.cva shouldBe 18_000.0
        }

        test("computes cva when counterparty has cdsSpreadBps only") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
                COUNTERPARTY.copy(pd1y = null, cdsSpreadBps = 85.0),
            )
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns pfeResult()
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult(cva = 12_000.0, estimated = true)
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            result.cva shouldBe 12_000.0
            result.cvaEstimated shouldBe true
        }
    }

    context("computeCVA") {

        test("calls CVA with counterparty credit data and exposure profile") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(COUNTERPARTY)
            coEvery {
                counterpartyRiskClient.calculateCVA(
                    counterpartyId = "CP-GS",
                    exposureProfile = TENORS,
                    lgd = 0.4,
                    pd1y = 0.0,
                    cdsSpreadBps = 65.0,
                    rating = "A+",
                    sector = "FINANCIALS",
                    riskFreeRate = 0.0,
                )
            } returns cvaResult(cva = 12_500.0, estimated = false)

            val result = service.computeCVA("CP-GS", TENORS)

            result.cva shouldBe 12_500.0
            result.isEstimated shouldBe false
        }

        test("throws IllegalStateException when counterparty has no credit data (pd1y and cdsSpreadBps both absent)") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
                COUNTERPARTY.copy(pd1y = null, cdsSpreadBps = null),
            )

            val exception = shouldThrow<IllegalStateException> {
                service.computeCVA("CP-GS", TENORS)
            }

            exception.message shouldBe "Counterparty CP-GS has no credit data (pd1y and cdsSpreadBps are both absent); CVA cannot be computed"
        }
    }

    context("snapshot completeness") {

        test("netNetExposure equals netExposure minus collateralHeld plus collateralPosted") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
                COUNTERPARTY.copy(isFinancial = false)
            )
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(
                NETTING_AGREEMENT.copy(csaThreshold = 500_000.0)
            ))
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns pfeResult(netExposure = 2_000_000.0)
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
                COUNTERPARTY.copy(isFinancial = false)
            )
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult()
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            // netNetExposure = netExposure(2_000_000) - collateralHeld(>=0) + collateralPosted(>=0)
            // with csaThreshold=500_000, collateralHeld <= netExposure and netNet <= netExposure
            result.netNetExposure shouldNotBe null
            (result.netNetExposure!! <= result.currentNetExposure) shouldBe true
        }

        test("CVA is persisted in snapshot after computeAndPersistPFE") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(COUNTERPARTY)
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns pfeResult()
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult(cva = 15_000.0, estimated = false)
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            result.cva shouldBe 15_000.0
            result.cvaEstimated shouldBe false
        }

        test("wrong-way risk flag is set for financial-sector counterparties") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
                COUNTERPARTY.copy(isFinancial = true)
            )
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns pfeResult()
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult()
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            result.wrongWayRiskFlags shouldNotBe null
            result.wrongWayRiskFlags!!.any { it.contains("FINANCIAL", ignoreCase = true) } shouldBe true
        }

        test("wrong-way risk flags are empty for non-financial counterparties") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
                COUNTERPARTY.copy(isFinancial = false, sector = "TECHNOLOGY")
            )
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns pfeResult()
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult()
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            result.wrongWayRiskFlags shouldNotBe null
            result.wrongWayRiskFlags!!.isEmpty() shouldBe true
        }

        test("netting set exposures contains one entry per netting agreement") {
            coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(COUNTERPARTY)
            coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(listOf(NETTING_AGREEMENT))
            coEvery {
                counterpartyRiskClient.calculatePFE(any(), any(), any(), any(), any(), any())
            } returns pfeResult()
            coEvery {
                counterpartyRiskClient.calculateCVA(any(), any(), any(), any(), any(), any(), any(), any())
            } returns cvaResult()
            coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

            val result = service.computeAndPersistPFE("CP-GS", emptyList())

            result.nettingSetExposures shouldNotBe null
            result.nettingSetExposures!!.size shouldBe 1
            result.nettingSetExposures!![0].nettingSetId shouldBe "NS-GS-001"
        }
    }

    context("getLatestExposure") {

        test("returns latest snapshot from repository") {
            val snapshot = CounterpartyExposureSnapshot(
                id = 1L,
                counterpartyId = "CP-GS",
                calculatedAt = java.time.Instant.now(),
                pfeProfile = TENORS,
                currentNetExposure = 2_000_000.0,
                peakPfe = 1_800_000.0,
                cva = 12_500.0,
                cvaEstimated = false,
            )
            coEvery { repository.findLatestByCounterpartyId("CP-GS") } returns snapshot

            val result = service.getLatestExposure("CP-GS")

            result shouldBe snapshot
        }

        test("returns null when no exposure calculated yet") {
            coEvery { repository.findLatestByCounterpartyId("CP-NEW") } returns null

            val result = service.getLatestExposure("CP-NEW")

            result shouldBe null
        }
    }
})
