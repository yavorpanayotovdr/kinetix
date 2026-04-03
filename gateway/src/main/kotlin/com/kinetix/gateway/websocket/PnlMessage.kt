package com.kinetix.gateway.websocket

import com.kinetix.common.kafka.events.IntradayPnlEvent
import kotlinx.serialization.Serializable

@Serializable
data class PnlSubscribeMessage(
    val type: String,
    val bookId: String,
)

@Serializable
data class PnlUpdate(
    val type: String = "pnl",
    val bookId: String,
    val snapshotAt: String,
    val baseCurrency: String,
    val trigger: String,
    val totalPnl: String,
    val realisedPnl: String,
    val unrealisedPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val vannaPnl: String,
    val volgaPnl: String,
    val charmPnl: String,
    val crossGammaPnl: String,
    val unexplainedPnl: String,
    val highWaterMark: String,
    val correlationId: String? = null,
    val missingFxRates: List<String> = emptyList(),
) {
    companion object {
        fun from(event: IntradayPnlEvent): PnlUpdate = PnlUpdate(
            bookId = event.bookId,
            snapshotAt = event.snapshotAt,
            baseCurrency = event.baseCurrency,
            trigger = event.trigger,
            totalPnl = event.totalPnl,
            realisedPnl = event.realisedPnl,
            unrealisedPnl = event.unrealisedPnl,
            deltaPnl = event.deltaPnl,
            gammaPnl = event.gammaPnl,
            vegaPnl = event.vegaPnl,
            thetaPnl = event.thetaPnl,
            rhoPnl = event.rhoPnl,
            vannaPnl = event.vannaPnl,
            volgaPnl = event.volgaPnl,
            charmPnl = event.charmPnl,
            crossGammaPnl = event.crossGammaPnl,
            unexplainedPnl = event.unexplainedPnl,
            highWaterMark = event.highWaterMark,
            correlationId = event.correlationId,
            missingFxRates = event.missingFxRates,
        )
    }
}
