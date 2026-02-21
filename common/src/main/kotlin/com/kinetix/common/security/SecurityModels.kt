package com.kinetix.common.security

enum class Role { ADMIN, TRADER, RISK_MANAGER, COMPLIANCE, VIEWER }

enum class Permission {
    READ_PORTFOLIOS, WRITE_TRADES, READ_POSITIONS,
    READ_RISK, CALCULATE_RISK,
    READ_REGULATORY, GENERATE_REPORTS,
    MANAGE_ALERTS, READ_ALERTS,
    READ_AUDIT, MANAGE_USERS,
}

val ROLE_PERMISSIONS: Map<Role, Set<Permission>> = mapOf(
    Role.ADMIN to Permission.entries.toSet(),
    Role.TRADER to setOf(
        Permission.READ_PORTFOLIOS, Permission.WRITE_TRADES, Permission.READ_POSITIONS,
        Permission.READ_RISK, Permission.READ_ALERTS,
    ),
    Role.RISK_MANAGER to setOf(
        Permission.READ_PORTFOLIOS, Permission.READ_POSITIONS,
        Permission.READ_RISK, Permission.CALCULATE_RISK,
        Permission.READ_ALERTS, Permission.MANAGE_ALERTS,
    ),
    Role.COMPLIANCE to setOf(
        Permission.READ_PORTFOLIOS, Permission.READ_POSITIONS,
        Permission.READ_RISK, Permission.READ_REGULATORY, Permission.GENERATE_REPORTS,
        Permission.READ_AUDIT, Permission.READ_ALERTS,
    ),
    Role.VIEWER to setOf(
        Permission.READ_PORTFOLIOS, Permission.READ_POSITIONS,
        Permission.READ_RISK, Permission.READ_ALERTS,
    ),
)

data class UserPrincipal(val userId: String, val username: String, val roles: Set<Role>) {
    fun hasPermission(permission: Permission): Boolean =
        roles.any { role -> ROLE_PERMISSIONS[role]?.contains(permission) == true }

    fun hasRole(role: Role): Boolean = roles.contains(role)
}
