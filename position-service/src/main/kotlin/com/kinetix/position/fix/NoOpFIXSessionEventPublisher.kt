package com.kinetix.position.fix

import org.slf4j.LoggerFactory

class NoOpFIXSessionEventPublisher : FIXSessionEventPublisher {
    private val logger = LoggerFactory.getLogger(NoOpFIXSessionEventPublisher::class.java)

    override suspend fun publishDisconnected(event: FIXSessionDisconnectedEvent) {
        logger.info(
            "FIX_SESSION_DISCONNECTED: sessionId={} counterparty={}",
            event.sessionId, event.counterparty,
        )
    }
}
