package com.kinetix.risk.service

import com.kinetix.common.model.Position
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.PositionSnapshotEntry
import com.kinetix.risk.model.VaRCalculationRequest
import java.security.MessageDigest

object InputDigest {

    fun digestPositions(positions: List<Position>): String {
        val canonical = positions
            .sortedBy { it.instrumentId.value }
            .joinToString(separator = "|") { pos ->
                "${pos.instrumentId.value}:${pos.assetClass.name}:${pos.quantity.toPlainString()}:" +
                    "${pos.averageCost.amount.toPlainString()}:${pos.marketPrice.amount.toPlainString()}:" +
                    "${pos.marketValue.amount.toPlainString()}:${pos.marketPrice.currency}"
            }
        return sha256(canonical)
    }

    fun digestPositionEntries(entries: List<PositionSnapshotEntry>): String {
        val canonical = entries
            .sortedBy { it.instrumentId }
            .joinToString(separator = "|") { e ->
                "${e.instrumentId}:${e.assetClass}:${e.quantity.toPlainString()}:" +
                    "${e.averageCostAmount.toPlainString()}:${e.marketPriceAmount.toPlainString()}:" +
                    "${e.marketValueAmount.toPlainString()}:${e.currency}"
            }
        return sha256(canonical)
    }

    fun digestMarketData(marketData: List<MarketDataValue>): String {
        val canonical = marketData
            .sortedWith(compareBy({ it.dataType }, { it.instrumentId }))
            .joinToString(separator = "|") { md ->
                "${md.dataType}:${md.instrumentId}:${md.assetClass}:${md.canonicalValue()}"
            }
        return sha256(canonical)
    }

    fun digestMarketDataBlob(blob: String): String = sha256(blob)

    fun digestOutputs(varValue: Double?, expectedShortfall: Double?): String {
        val canonical = listOf(
            "var=${varValue?.let { "%.15g".format(it) } ?: "null"}",
            "es=${expectedShortfall?.let { "%.15g".format(it) } ?: "null"}",
        ).joinToString(separator = "|")
        return sha256(canonical)
    }

    fun digestInputs(
        request: VaRCalculationRequest,
        positionDigest: String,
        marketDataDigest: String,
        modelVersion: String,
    ): String {
        val canonical = listOf(
            "calc=${request.calculationType.name}",
            "conf=${request.confidenceLevel.name}",
            "horizon=${request.timeHorizonDays}",
            "sims=${request.numSimulations}",
            "seed=${request.monteCarloSeed}",
            "model=$modelVersion",
            "positions=$positionDigest",
            "marketData=$marketDataDigest",
        ).joinToString(separator = "|")
        return sha256(canonical)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun MarketDataValue.canonicalValue(): String = when (this) {
        is com.kinetix.risk.model.ScalarMarketData -> "%.15g".format(value)
        is com.kinetix.risk.model.TimeSeriesMarketData ->
            points.joinToString(",") { "${it.timestamp}:${"%.15g".format(it.value)}" }
        is com.kinetix.risk.model.CurveMarketData ->
            points.joinToString(",") { "${it.tenor}:${"%.15g".format(it.value)}" }
        is com.kinetix.risk.model.MatrixMarketData ->
            "${rows.joinToString(",")}|${columns.joinToString(",")}|${values.joinToString(",") { "%.15g".format(it) }}"
    }
}
