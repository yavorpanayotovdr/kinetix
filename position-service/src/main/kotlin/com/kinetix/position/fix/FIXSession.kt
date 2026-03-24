package com.kinetix.position.fix

import java.time.Instant

data class FIXSession(
    val sessionId: String,
    val counterparty: String,
    val status: FIXSessionStatus,
    val lastMessageAt: Instant?,
    val inboundSeqNum: Int,
    val outboundSeqNum: Int,
)
