package com.kinetix.position.fix

import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Converts raw FIX tag=value messages (represented as pipe-delimited or SOH-delimited strings)
 * into typed internal events.
 *
 * This is an internal representation — it does not depend on QuickFIX/J or any FIX library.
 * The real fix-adapter service (future extraction) will supply parsed messages here;
 * in tests we build messages using the pipe-delimited format from the test helpers.
 *
 * FIX tags used:
 *   35  = MsgType          (8 = ExecutionReport)
 *   11  = ClOrdID
 *   17  = ExecID
 *   37  = OrderID
 *   150 = ExecType         (F = Fill, 1 = Partial Fill, 4 = Cancelled, 5 = Replace)
 *   32  = LastQty
 *   31  = LastPx
 *   14  = CumQty
 *   6   = AvgPx
 *   30  = LastMkt (venue, optional)
 */
class FIXMessageConverter {

    private val logger = LoggerFactory.getLogger(FIXMessageConverter::class.java)

    /**
     * Parses a FIX-style tag=value message into a [FIXInboundFillEvent].
     *
     * Returns null when:
     * - The message is not an ExecutionReport (MsgType != 8)
     * - lastQty <= 0 or lastPrice < 0 (invalid fill per spec)
     * - Required tags are missing
     *
     * The session_id is supplied separately because it is a session-level attribute,
     * not present in every FIX application-layer message.
     */
    fun parseExecutionReport(
        rawMessage: String,
        sessionId: String = "UNKNOWN",
    ): FIXInboundFillEvent? {
        val tags = parseTags(rawMessage)

        val msgType = tags["35"] ?: run {
            logger.warn("FIX message missing MsgType (tag 35)")
            return null
        }
        if (msgType != "8") {
            logger.debug("Skipping non-ExecutionReport message type: {}", msgType)
            return null
        }

        val execType = tags["150"] ?: run {
            logger.warn("ExecutionReport missing ExecType (tag 150)")
            return null
        }
        val execId = tags["17"] ?: run {
            logger.warn("ExecutionReport missing ExecID (tag 17)")
            return null
        }
        val orderId = tags["37"] ?: run {
            logger.warn("ExecutionReport missing OrderID (tag 37)")
            return null
        }

        val lastQtyRaw = tags["32"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val lastPriceRaw = tags["31"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val cumQty = tags["14"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val avgPx = tags["6"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        // Validate fill quantities for fill execTypes — per RejectInvalidFill rule
        if (execType in setOf("F", "1") && lastQtyRaw.signum() <= 0) {
            logger.error("Rejecting fill with zero/negative LastQty: execId={}, lastQty={}", execId, lastQtyRaw)
            return null
        }
        if (execType in setOf("F", "1") && lastPriceRaw < BigDecimal.ZERO) {
            logger.error("Rejecting fill with negative LastPx: execId={}, lastPx={}", execId, lastPriceRaw)
            return null
        }

        return FIXInboundFillEvent(
            sessionId = sessionId,
            execId = execId,
            orderId = orderId,
            execType = execType,
            lastQty = lastQtyRaw,
            lastPrice = lastPriceRaw,
            cumulativeQty = cumQty,
            averagePrice = avgPx,
            venue = tags["30"],
        )
    }

    private fun parseTags(rawMessage: String): Map<String, String> {
        // Support both SOH (ASCII 0x01) and pipe as field delimiter
        val delimiter = if (rawMessage.contains('\u0001')) '\u0001' else '|'
        return rawMessage.split(delimiter)
            .mapNotNull { field ->
                val eq = field.indexOf('=')
                if (eq > 0) field.substring(0, eq) to field.substring(eq + 1) else null
            }
            .toMap()
    }

    companion object {
        /**
         * Deterministically derives a fill_id from (sessionId, execId) using SHA-256.
         * This guarantees the same FIX fill always maps to the same internal fill_id
         * making reprocessing on reconnect idempotent.
         */
        fun deterministicFillId(sessionId: String, execId: String): String {
            val input = "$sessionId:$execId"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}
