package com.kinetix.gateway.auth

import com.kinetix.common.security.Role
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*

class PrincipalExtractionTest : FunSpec({

    val jwtConfig = TestJwtHelper.testJwtConfig()
    val jwkProvider = TestJwtHelper.testJwkProvider()

    test("requireUserId returns the JWT sub claim") {
        val token = TestJwtHelper.generateToken(userId = "trader-42", username = "johndoe", roles = listOf(Role.TRADER))

        testApplication {
            application {
                module()
                configureJwtAuth(jwtConfig, jwkProvider)
                routing {
                    authenticate("auth-jwt") {
                        get("/test-principal") {
                            val userId = call.requireUserId()
                            call.respondText(userId)
                        }
                    }
                }
            }

            val response = client.get("/test-principal") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "trader-42"
        }
    }

    test("requireUserId returns sub, not preferred_username, when they differ") {
        val token = TestJwtHelper.generateToken(userId = "user-id-123", username = "display_name", roles = listOf(Role.TRADER))

        testApplication {
            application {
                module()
                configureJwtAuth(jwtConfig, jwkProvider)
                routing {
                    authenticate("auth-jwt") {
                        get("/test-principal") {
                            val userId = call.requireUserId()
                            call.respondText(userId)
                        }
                    }
                }
            }

            val response = client.get("/test-principal") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "user-id-123"
        }
    }

    test("requireUserId throws IllegalStateException when called outside authenticate block") {
        testApplication {
            application {
                module()
                routing {
                    get("/test-no-auth") {
                        val userId = call.requireUserId()
                        call.respondText(userId)
                    }
                }
            }

            val response = client.get("/test-no-auth")
            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }
})
