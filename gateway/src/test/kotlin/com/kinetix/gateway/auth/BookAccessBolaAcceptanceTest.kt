package com.kinetix.gateway.auth

import com.kinetix.common.security.Role
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*

/**
 * Acceptance tests verifying that BOLA prevention is wired across all book-scoped
 * route families — not just positions/trades (covered by BookAccessAcceptanceTest).
 *
 * Each test exercises a representative route from a different permission group
 * to prove the requireBookAccess() plugin is wired to all route groups.
 */
class BookAccessBolaAcceptanceTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>()
    val jwtConfig = TestJwtHelper.testJwtConfig()
    val jwkProvider = TestJwtHelper.testJwkProvider()

    val bookAccessService = InMemoryBookAccessService(
        traderBooks = mapOf("trader-1" to setOf("book-A"))
    )

    beforeEach { clearMocks(riskClient) }

    // --- CALCULATE_RISK permission group (RISK_MANAGER has this, TRADER does not) ---

    test("RISK_MANAGER accessing own-assigned book via VaR route receives response") {
        coEvery { riskClient.calculateVaR(any()) } returns mockk(relaxed = true)
        val rmBookAccess = InMemoryBookAccessService(
            traderBooks = mapOf("rm-1" to setOf("book-A"))
        )
        val token = TestJwtHelper.generateToken(userId = "rm-1", roles = listOf(Role.RISK_MANAGER))

        testApplication {
            application { module(jwtConfig, riskClient = riskClient, bookAccessService = rmBookAccess, jwkProvider = jwkProvider) }
            val response = client.post("/api/v1/risk/var/book-A") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("RISK_MANAGER can access any book via VaR route (unrestricted role)") {
        coEvery { riskClient.calculateVaR(any()) } returns mockk(relaxed = true)
        val token = TestJwtHelper.generateToken(userId = "rm-1", roles = listOf(Role.RISK_MANAGER))

        testApplication {
            application { module(jwtConfig, riskClient = riskClient, bookAccessService = bookAccessService, jwkProvider = jwkProvider) }
            val response = client.post("/api/v1/risk/var/any-book") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            response.status shouldBe HttpStatusCode.OK
        }
    }

    // --- READ_RISK permission group (TRADER has READ_RISK) ---

    test("TRADER accessing own book via stress test route receives response") {
        coEvery { riskClient.runStressTest(any()) } returns mockk(relaxed = true)
        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, riskClient = riskClient, bookAccessService = bookAccessService, jwkProvider = jwkProvider) }
            val response = client.post("/api/v1/risk/stress/book-A") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"scenarioName":"GFC 2008"}""")
            }
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("TRADER accessing unassigned book via stress test route receives 403") {
        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, riskClient = riskClient, bookAccessService = bookAccessService, jwkProvider = jwkProvider) }
            val response = client.post("/api/v1/risk/stress/book-B") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"scenarioName":"GFC 2008"}""")
            }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    // --- Cross-book VaR (bookIds in body, under CALCULATE_RISK — use RISK_MANAGER) ---

    test("RISK_MANAGER with access to all listed books can run cross-book VaR") {
        coEvery { riskClient.calculateCrossBookVaR(any()) } returns mockk(relaxed = true)
        val token = TestJwtHelper.generateToken(userId = "rm-1", roles = listOf(Role.RISK_MANAGER))

        testApplication {
            application { module(jwtConfig, riskClient = riskClient, bookAccessService = bookAccessService, jwkProvider = jwkProvider) }
            val response = client.post("/api/v1/risk/var/cross-book") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"bookIds":["book-A"],"portfolioGroupId":"group-1"}""")
            }
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("TRADER-like RISK_MANAGER with partial book ownership on cross-book VaR receives 403 naming denied book") {
        // Use a custom BookAccessService that restricts rm-1 to book-A only
        // (even though RM is normally unrestricted, we test the multi-book check logic)
        val restrictedService = object : BookAccessService {
            override fun canAccess(principal: com.kinetix.common.security.UserPrincipal, bookId: String): Boolean {
                return bookId == "book-A"
            }
        }
        val token = TestJwtHelper.generateToken(userId = "rm-1", roles = listOf(Role.RISK_MANAGER))

        testApplication {
            application { module(jwtConfig, riskClient = riskClient, bookAccessService = restrictedService, jwkProvider = jwkProvider) }
            val response = client.post("/api/v1/risk/var/cross-book") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"bookIds":["book-A","book-C"],"portfolioGroupId":"group-1"}""")
            }
            response.status shouldBe HttpStatusCode.Forbidden
            response.bodyAsText() shouldContain "book-C"
        }
    }

    // --- TRADER with no assignments ---

    test("TRADER with no book assignments receives 403 on book-scoped routes") {
        val bookAccessNoAssignments = InMemoryBookAccessService(traderBooks = emptyMap())
        val token = TestJwtHelper.generateToken(userId = "trader-orphan", roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, riskClient = riskClient, bookAccessService = bookAccessNoAssignments, jwkProvider = jwkProvider) }
            val response = client.post("/api/v1/risk/stress/any-book") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"scenarioName":"GFC 2008"}""")
            }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }
})
