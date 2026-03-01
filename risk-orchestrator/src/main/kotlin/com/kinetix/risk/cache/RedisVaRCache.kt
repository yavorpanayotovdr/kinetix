package com.kinetix.risk.cache

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.*
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RedisVaRCache(
    private val connection: StatefulRedisConnection<String, String>,
    private val ttlSeconds: Long = 300L,
) : VaRCache {

    private val sync = connection.sync()

    override fun put(portfolioId: String, result: ValuationResult) {
        val key = keyFor(portfolioId)
        val value = Json.encodeToString(CachedValuationResult.from(result))
        sync.set(key, value, SetArgs().ex(ttlSeconds))
    }

    override fun get(portfolioId: String): ValuationResult? {
        val key = keyFor(portfolioId)
        val value = sync.get(key) ?: return null
        return Json.decodeFromString<CachedValuationResult>(value).toValuationResult()
    }

    private fun keyFor(portfolioId: String): String = "var:$portfolioId"
}

@Serializable
internal data class CachedComponentBreakdown(
    val assetClass: String,
    val varContribution: Double,
    val percentageOfTotal: Double,
)

@Serializable
internal data class CachedGreekValues(
    val assetClass: String,
    val delta: Double,
    val gamma: Double,
    val vega: Double,
)

@Serializable
internal data class CachedGreeksResult(
    val assetClassGreeks: List<CachedGreekValues>,
    val theta: Double,
    val rho: Double,
)

@Serializable
internal data class CachedPositionRisk(
    val instrumentId: String,
    val assetClass: String,
    val marketValue: String,
    val delta: Double?,
    val gamma: Double?,
    val vega: Double?,
    val varContribution: String,
    val esContribution: String,
    val percentageOfTotal: String,
)

@Serializable
internal data class CachedValuationResult(
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: Double?,
    val expectedShortfall: Double?,
    val componentBreakdown: List<CachedComponentBreakdown>,
    val greeks: CachedGreeksResult?,
    val calculatedAt: String,
    val computedOutputs: List<String>,
    val pvValue: Double?,
    val positionRisk: List<CachedPositionRisk>,
    val jobId: String?,
) {
    fun toValuationResult(): ValuationResult = ValuationResult(
        portfolioId = PortfolioId(portfolioId),
        calculationType = CalculationType.valueOf(calculationType),
        confidenceLevel = ConfidenceLevel.valueOf(confidenceLevel),
        varValue = varValue,
        expectedShortfall = expectedShortfall,
        componentBreakdown = componentBreakdown.map {
            ComponentBreakdown(AssetClass.valueOf(it.assetClass), it.varContribution, it.percentageOfTotal)
        },
        greeks = greeks?.let { g ->
            GreeksResult(
                assetClassGreeks = g.assetClassGreeks.map {
                    GreekValues(AssetClass.valueOf(it.assetClass), it.delta, it.gamma, it.vega)
                },
                theta = g.theta,
                rho = g.rho,
            )
        },
        calculatedAt = Instant.parse(calculatedAt),
        computedOutputs = computedOutputs.map { ValuationOutput.valueOf(it) }.toSet(),
        pvValue = pvValue,
        positionRisk = positionRisk.map {
            PositionRisk(
                instrumentId = InstrumentId(it.instrumentId),
                assetClass = AssetClass.valueOf(it.assetClass),
                marketValue = BigDecimal(it.marketValue),
                delta = it.delta,
                gamma = it.gamma,
                vega = it.vega,
                varContribution = BigDecimal(it.varContribution),
                esContribution = BigDecimal(it.esContribution),
                percentageOfTotal = BigDecimal(it.percentageOfTotal),
            )
        },
        jobId = jobId?.let { UUID.fromString(it) },
    )

    companion object {
        fun from(result: ValuationResult): CachedValuationResult = CachedValuationResult(
            portfolioId = result.portfolioId.value,
            calculationType = result.calculationType.name,
            confidenceLevel = result.confidenceLevel.name,
            varValue = result.varValue,
            expectedShortfall = result.expectedShortfall,
            componentBreakdown = result.componentBreakdown.map {
                CachedComponentBreakdown(it.assetClass.name, it.varContribution, it.percentageOfTotal)
            },
            greeks = result.greeks?.let { g ->
                CachedGreeksResult(
                    assetClassGreeks = g.assetClassGreeks.map {
                        CachedGreekValues(it.assetClass.name, it.delta, it.gamma, it.vega)
                    },
                    theta = g.theta,
                    rho = g.rho,
                )
            },
            calculatedAt = result.calculatedAt.toString(),
            computedOutputs = result.computedOutputs.map { it.name },
            pvValue = result.pvValue,
            positionRisk = result.positionRisk.map {
                CachedPositionRisk(
                    instrumentId = it.instrumentId.value,
                    assetClass = it.assetClass.name,
                    marketValue = it.marketValue.toPlainString(),
                    delta = it.delta,
                    gamma = it.gamma,
                    vega = it.vega,
                    varContribution = it.varContribution.toPlainString(),
                    esContribution = it.esContribution.toPlainString(),
                    percentageOfTotal = it.percentageOfTotal.toPlainString(),
                )
            },
            jobId = result.jobId?.toString(),
        )
    }
}
