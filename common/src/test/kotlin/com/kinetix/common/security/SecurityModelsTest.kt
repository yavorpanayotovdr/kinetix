package com.kinetix.common.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

class SecurityModelsTest : FunSpec({

    test("Role has five values") {
        Role.entries.size shouldBe 5
        Role.entries.map { it.name } shouldContainAll listOf(
            "ADMIN", "TRADER", "RISK_MANAGER", "COMPLIANCE", "VIEWER",
        )
    }

    test("Permission has expected values") {
        Permission.entries.map { it.name } shouldContainAll listOf(
            "READ_PORTFOLIOS", "WRITE_TRADES", "READ_POSITIONS",
            "READ_RISK", "CALCULATE_RISK",
            "READ_REGULATORY", "GENERATE_REPORTS",
            "MANAGE_ALERTS", "READ_ALERTS",
            "READ_AUDIT", "MANAGE_USERS",
        )
    }

    test("ADMIN role has all permissions") {
        ROLE_PERMISSIONS[Role.ADMIN] shouldBe Permission.entries.toSet()
    }

    test("TRADER role has trade-related permissions") {
        val traderPerms = ROLE_PERMISSIONS[Role.TRADER]!!
        traderPerms shouldContainAll setOf(
            Permission.READ_PORTFOLIOS, Permission.WRITE_TRADES,
            Permission.READ_POSITIONS, Permission.READ_RISK, Permission.READ_ALERTS,
        )
        (Permission.CALCULATE_RISK in traderPerms) shouldBe false
        (Permission.MANAGE_USERS in traderPerms) shouldBe false
    }

    test("RISK_MANAGER role has risk-related permissions") {
        val riskPerms = ROLE_PERMISSIONS[Role.RISK_MANAGER]!!
        riskPerms shouldContainAll setOf(
            Permission.READ_PORTFOLIOS, Permission.READ_POSITIONS,
            Permission.READ_RISK, Permission.CALCULATE_RISK,
            Permission.READ_ALERTS, Permission.MANAGE_ALERTS,
        )
        (Permission.WRITE_TRADES in riskPerms) shouldBe false
    }

    test("COMPLIANCE role has regulatory and audit permissions") {
        val compliancePerms = ROLE_PERMISSIONS[Role.COMPLIANCE]!!
        compliancePerms shouldContainAll setOf(
            Permission.READ_REGULATORY, Permission.GENERATE_REPORTS,
            Permission.READ_AUDIT, Permission.READ_ALERTS,
        )
        (Permission.WRITE_TRADES in compliancePerms) shouldBe false
        (Permission.MANAGE_USERS in compliancePerms) shouldBe false
    }

    test("VIEWER role has read-only permissions") {
        val viewerPerms = ROLE_PERMISSIONS[Role.VIEWER]!!
        viewerPerms shouldContainAll setOf(
            Permission.READ_PORTFOLIOS, Permission.READ_POSITIONS,
            Permission.READ_RISK, Permission.READ_ALERTS,
        )
        (Permission.WRITE_TRADES in viewerPerms) shouldBe false
        (Permission.CALCULATE_RISK in viewerPerms) shouldBe false
        (Permission.MANAGE_ALERTS in viewerPerms) shouldBe false
    }

    test("UserPrincipal.hasPermission checks role permissions") {
        val trader = UserPrincipal("u1", "trader1", setOf(Role.TRADER))
        trader.hasPermission(Permission.WRITE_TRADES) shouldBe true
        trader.hasPermission(Permission.CALCULATE_RISK) shouldBe false
    }

    test("UserPrincipal.hasRole checks role membership") {
        val user = UserPrincipal("u1", "admin1", setOf(Role.ADMIN, Role.TRADER))
        user.hasRole(Role.ADMIN) shouldBe true
        user.hasRole(Role.TRADER) shouldBe true
        user.hasRole(Role.VIEWER) shouldBe false
    }
})
