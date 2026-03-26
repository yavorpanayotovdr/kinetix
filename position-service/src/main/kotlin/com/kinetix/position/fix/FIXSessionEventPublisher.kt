package com.kinetix.position.fix

/**
 * Publishes FIX session lifecycle events so downstream consumers
 * (e.g. notification-service) can react to connectivity changes.
 */
interface FIXSessionEventPublisher {
    suspend fun publishDisconnected(event: FIXSessionDisconnectedEvent)
}
