package com.kinetix.risk.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.BookId
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.instrument.CorporateBond
import com.kinetix.common.model.instrument.GovernmentBond
import com.kinetix.common.model.instrument.InstrumentType
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.GrpcKrdClient
import com.kinetix.risk.client.InstrumentServiceClient
import com.kinetix.risk.client.KrdCalculationInput
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RatesServiceClient
import com.kinetix.risk.model.InstrumentKrdResult
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val DEFAULT_CURVE_ID = "USD-SWAP"
private const val DAYS_PER_YEAR = 365.25

class KeyRateDurationService(
    private val positionProvider: PositionProvider,
    private val ratesServiceClient: RatesServiceClient,
    private val grpcKrdClient: GrpcKrdClient,
    private val instrumentServiceClient: InstrumentServiceClient? = null,
    private val curveId: String = DEFAULT_CURVE_ID,
) {
    private val logger = LoggerFactory.getLogger(KeyRateDurationService::class.java)

    suspend fun calculate(bookId: BookId): List<InstrumentKrdResult> {
        val positions = positionProvider.getPositions(bookId)
        val fixedIncomePositions = positions.filter { it.assetClass == AssetClass.FIXED_INCOME }

        if (fixedIncomePositions.isEmpty()) {
            logger.info("No fixed-income positions found for book {}, skipping KRD calculation", bookId.value)
            return emptyList()
        }

        val yieldCurveResponse = ratesServiceClient.getLatestYieldCurve(curveId)
        if (yieldCurveResponse is ClientResponse.NotFound) {
            logger.warn("Yield curve {} not found, cannot compute KRD for book {}", curveId, bookId.value)
            return emptyList()
        }
        val yieldCurve = (yieldCurveResponse as ClientResponse.Success).value
        val curveTenors = yieldCurve.tenors.map { tenor -> tenor.days to tenor.rate }

        val instrumentMap: Map<String, InstrumentType?> = if (instrumentServiceClient != null) {
            val ids = fixedIncomePositions.map { InstrumentId(it.instrumentId.value) }
            val dtos = instrumentServiceClient.getInstruments(ids)
            dtos.mapValues { (_, dto) ->
                runCatching { dto.toInstrumentType() }.getOrNull()
            }
        } else {
            emptyMap()
        }

        val results = mutableListOf<InstrumentKrdResult>()
        for (position in fixedIncomePositions) {
            val instrumentType = instrumentMap[position.instrumentId.value]
            val bondParams = extractBondParams(position.instrumentId.value, instrumentType)

            try {
                val input = KrdCalculationInput(
                    instrumentId = position.instrumentId.value,
                    faceValue = BigDecimal.valueOf(bondParams.faceValue),
                    couponRate = BigDecimal.valueOf(bondParams.couponRate),
                    couponFrequency = bondParams.couponFrequency,
                    maturityYears = computeMaturityYears(bondParams.maturityDate),
                    yieldCurveTenors = curveTenors,
                )
                results.add(grpcKrdClient.calculateKrd(input))
            } catch (e: Exception) {
                logger.error(
                    "KRD calculation failed for instrument {} in book {}: {}",
                    position.instrumentId.value, bookId.value, e.message,
                )
            }
        }
        return results
    }

    private fun extractBondParams(instrumentId: String, instrumentType: InstrumentType?): BondParams {
        return when (instrumentType) {
            is GovernmentBond -> BondParams(
                faceValue = instrumentType.faceValue,
                couponRate = instrumentType.couponRate,
                couponFrequency = instrumentType.couponFrequency,
                maturityDate = instrumentType.maturityDate,
            )
            is CorporateBond -> BondParams(
                faceValue = instrumentType.faceValue,
                couponRate = instrumentType.couponRate,
                couponFrequency = instrumentType.couponFrequency,
                maturityDate = instrumentType.maturityDate,
            )
            else -> {
                // No instrument record available: fall back to unit-face defaults so the
                // endpoint can still return relative DV01 sensitivity profile.
                logger.debug("No bond attributes for {}, using unit-face defaults", instrumentId)
                BondParams(
                    faceValue = 1.0,
                    couponRate = 0.05,
                    couponFrequency = 2,
                    maturityDate = LocalDate.now().plusYears(10).toString(),
                )
            }
        }
    }

    private fun computeMaturityYears(maturityDate: String): BigDecimal {
        return try {
            val mat = LocalDate.parse(maturityDate)
            val daysToMaturity = ChronoUnit.DAYS.between(LocalDate.now(), mat).coerceAtLeast(1)
            BigDecimal.valueOf(daysToMaturity / DAYS_PER_YEAR)
        } catch (e: Exception) {
            logger.warn("Could not parse maturity date '{}', defaulting to 10 years", maturityDate)
            BigDecimal.TEN
        }
    }

    private data class BondParams(
        val faceValue: Double,
        val couponRate: Double,
        val couponFrequency: Int,
        val maturityDate: String,
    )
}
