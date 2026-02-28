package com.kinetix.audit.persistence

import com.kinetix.audit.model.AuditEvent
import java.security.MessageDigest

object AuditHasher {

    fun computeHash(event: AuditEvent, previousHash: String?): String {
        val data = buildString {
            append(event.id)
            append(event.receivedAt)
            append(event.tradeId)
            append(event.portfolioId)
            append(event.instrumentId)
            append(event.assetClass)
            append(event.side)
            append(event.quantity)
            append(event.priceAmount)
            append(event.priceCurrency)
            append(event.tradedAt)
            append(event.userId ?: "NULL")
            append(event.userRole ?: "NULL")
            append(event.eventType)
            append(previousHash ?: "NULL")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyChain(events: List<AuditEvent>): ChainVerificationResult {
        if (events.isEmpty()) {
            return ChainVerificationResult(valid = true, eventCount = 0)
        }

        var previousHash: String? = null
        for (event in events) {
            if (event.previousHash != previousHash) {
                return ChainVerificationResult(valid = false, eventCount = events.size)
            }
            val expectedHash = computeHash(event, previousHash)
            if (event.recordHash != expectedHash) {
                return ChainVerificationResult(valid = false, eventCount = events.size)
            }
            previousHash = event.recordHash
        }
        return ChainVerificationResult(valid = true, eventCount = events.size)
    }
}
