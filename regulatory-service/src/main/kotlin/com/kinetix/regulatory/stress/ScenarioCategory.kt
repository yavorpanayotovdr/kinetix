package com.kinetix.regulatory.stress

enum class ScenarioCategory {
    /** Scenario mandated by prudential regulation (Basel III/FRTB stress scenarios). */
    REGULATORY_MANDATED,

    /** Scenario designed and approved internally by the risk committee. */
    INTERNAL_APPROVED,

    /** Scenario requested by a supervisory authority for a specific review or SREP exercise. */
    SUPERVISORY_REQUESTED,
}
