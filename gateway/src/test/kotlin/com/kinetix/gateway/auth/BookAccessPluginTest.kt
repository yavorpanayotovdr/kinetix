package com.kinetix.gateway.auth

import com.kinetix.common.audit.AuditEventType
import com.kinetix.common.audit.GovernanceAuditEvent
import com.kinetix.common.security.Role
import com.kinetix.gateway.audit.GovernanceAuditPublisher
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.concurrent.Future

class BookAccessPluginTest : FunSpec({

    val jwtConfig = TestJwtHelper.testJwtConfig()
    val jwkProvider = TestJwtHelper.testJwkProvider()

    val bookAccessService = InMemoryBookAccessService(
        traderBooks = mapOf("trader-1" to setOf("book-A", "book-B"))
    )

    test("TRADER with access to the book passes through to the handler") {
        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application {
                module()
                configureJwtAuth(jwtConfig, jwkProvider)
                routing {
                    authenticate("auth-jwt") {
                        requireBookAccess(bookAccessService) {
                            get("/api/v1/risk/var/{bookId}") {
                                call.respondText("OK-${call.parameters["bookId"]}")
                            }
                        }
                    }
                }
            }

            val response = client.get("/api/v1/risk/var/book-A") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "OK-book-A"
        }
    }

    test("TRADER without access to the book receives 403") {
        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application {
                module()
                configureJwtAuth(jwtConfig, jwkProvider)
                routing {
                    authenticate("auth-jwt") {
                        requireBookAccess(bookAccessService) {
                            get("/api/v1/risk/var/{bookId}") {
                                call.respondText("should not reach")
                            }
                        }
                    }
                }
            }

            val response = client.get("/api/v1/risk/var/book-C") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.Forbidden
            response.bodyAsText() shouldContain "book-C"
        }
    }

    test("RISK_MANAGER can access any book regardless of assignments") {
        val token = TestJwtHelper.generateToken(userId = "rm-1", roles = listOf(Role.RISK_MANAGER))

        testApplication {
            application {
                module()
                configureJwtAuth(jwtConfig, jwkProvider)
                routing {
                    authenticate("auth-jwt") {
                        requireBookAccess(bookAccessService) {
                            get("/api/v1/risk/var/{bookId}") {
                                call.respondText("OK")
                            }
                        }
                    }
                }
            }

            val response = client.get("/api/v1/risk/var/any-book") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("route without bookId in path passes through regardless of role") {
        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application {
                module()
                configureJwtAuth(jwtConfig, jwkProvider)
                routing {
                    authenticate("auth-jwt") {
                        requireBookAccess(bookAccessService) {
                            get("/api/v1/books") {
                                call.respondText("list-all")
                            }
                        }
                    }
                }
            }

            val response = client.get("/api/v1/books") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "list-all"
        }
    }

    test("publishes BOOK_ACCESS_DENIED audit event on denial") {
        val producer = mockk<KafkaProducer<String, String>>()
        val publishedSlot = slot<org.apache.kafka.clients.producer.ProducerRecord<String, String>>()
        every { producer.send(capture(publishedSlot)) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        val auditPublisher = GovernanceAuditPublisher(producer, topic = "governance.audit")

        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application {
                module()
                configureJwtAuth(jwtConfig, jwkProvider)
                routing {
                    authenticate("auth-jwt") {
                        requireBookAccess(bookAccessService, auditPublisher) {
                            get("/api/v1/risk/var/{bookId}") {
                                call.respondText("should not reach")
                            }
                        }
                    }
                }
            }

            client.get("/api/v1/risk/var/book-C") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }

        verify(atLeast = 1) { producer.send(any()) }
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString<GovernanceAuditEvent>(publishedSlot.captured.value())
        decoded.eventType shouldBe AuditEventType.BOOK_ACCESS_DENIED
        decoded.userId shouldBe "trader-1"
        decoded.details shouldContain "book-C"
    }

    test("empty bookId in path returns 400") {
        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application {
                module()
                configureJwtAuth(jwtConfig, jwkProvider)
                routing {
                    authenticate("auth-jwt") {
                        requireBookAccess(bookAccessService) {
                            get("/api/v1/risk/var/{bookId}") {
                                call.respondText("should not reach")
                            }
                        }
                    }
                }
            }

            // URL-encoded empty string — Ktor resolves {bookId} to empty string for /var/%20
            // But a truly empty path segment /var// becomes a different route. Test with whitespace:
            val response = client.get("/api/v1/risk/var/%20") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
