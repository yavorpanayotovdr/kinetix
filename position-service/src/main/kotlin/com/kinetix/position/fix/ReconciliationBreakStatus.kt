package com.kinetix.position.fix

/**
 * Lifecycle status of an individual reconciliation break.
 *
 * OPEN        — newly identified, not yet under investigation
 * INVESTIGATING — someone is actively researching the break
 * RESOLVED    — the break has been explained and closed
 */
enum class ReconciliationBreakStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED,
}
