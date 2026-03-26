package com.kinetix.gateway.auth

import com.kinetix.common.audit.AuditEventType
import com.kinetix.common.audit.GovernanceAuditEvent
import com.kinetix.gateway.audit.GovernanceAuditPublisher
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Route-scoped plugin that enforces book-level access control.
 * Mirrors the [requirePermission] pattern using [AuthenticationChecked] hook.
 *
 * Behaviour:
 * - If no `{bookId}` path parameter exists in the matched route, passes through (list endpoints).
 * - If bookId is blank, responds 400.
 * - Delegates to [BookAccessService.canAccess] for ownership check.
 * - On denial, responds 403 and publishes a [AuditEventType.BOOK_ACCESS_DENIED] audit event.
 */
fun Route.requireBookAccess(
    bookAccessService: BookAccessService,
    auditPublisher: GovernanceAuditPublisher? = null,
    build: Route.() -> Unit,
): Route {
    val route = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Transparent
    })
    route.install(
        createRouteScopedPlugin("BookAccessCheck") {
            on(AuthenticationChecked) { call ->
                val bookId = call.parameters["bookId"] ?: return@on // no bookId in path — pass through

                if (bookId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "bad_request", "message" to "bookId must not be empty"))
                    return@on
                }

                val principal = call.principal<JwtUserPrincipal>() ?: return@on // unauthenticated — handled by auth layer

                if (!bookAccessService.canAccess(principal.user, bookId)) {
                    auditPublisher?.publish(
                        GovernanceAuditEvent(
                            eventType = AuditEventType.BOOK_ACCESS_DENIED,
                            userId = principal.user.userId,
                            userRole = principal.user.roles.joinToString(",") { it.name },
                            details = "Denied access to book $bookId for ${call.request.local.uri}",
                        )
                    )
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "forbidden", "message" to "Access to book $bookId is not permitted"),
                    )
                }
            }
        },
    )
    route.build()
    return route
}
