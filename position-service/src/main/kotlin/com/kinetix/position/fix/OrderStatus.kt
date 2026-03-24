// NOTE: This package (com.kinetix.position.fix) is intended for future extraction
// into a standalone fix-adapter service once the Gradle multi-module setup permits.
// See docs/plans/trader-review-team-plan-23.03.2026.md — Direction 5.

package com.kinetix.position.fix

enum class OrderStatus {
    PENDING_RISK_CHECK,
    APPROVED,
    REJECTED,
    SENT,
    PARTIAL,
    FILLED,
    CANCELLED,
    EXPIRED;

    val isTerminal: Boolean
        get() = this in setOf(FILLED, CANCELLED, EXPIRED, REJECTED)
}
