package com.kinetix.regulatory

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.dto.ErrorResponse
import com.kinetix.regulatory.persistence.DatabaseConfig
import com.kinetix.regulatory.persistence.DatabaseFactory
import com.kinetix.regulatory.persistence.ExposedFrtbCalculationRepository
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import com.kinetix.regulatory.routes.regulatoryRoutes
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = appMicrometerRegistry }
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "Bad Request", message = cause.message ?: "Invalid request"),
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error = "Internal Server Error", message = cause.message ?: "Unexpected error"),
            )
        }
    }
    routing {
        get("/health") {
            call.respondText("""{"status":"UP"}""", ContentType.Application.Json)
        }
        get("/metrics") {
            call.respondText(appMicrometerRegistry.scrape())
        }
    }
}

fun Application.module(repository: FrtbCalculationRepository, client: RiskOrchestratorClient) {
    module()
    routing {
        regulatoryRoutes(repository, client)
    }
}

fun Application.moduleWithRoutes() {
    val dbConfig = environment.config.config("database")
    val db = DatabaseFactory.init(
        DatabaseConfig(
            jdbcUrl = dbConfig.property("jdbcUrl").getString(),
            username = dbConfig.property("username").getString(),
            password = dbConfig.property("password").getString(),
        ),
    )

    val repository = ExposedFrtbCalculationRepository(db)

    val riskOrchestratorUrl = environment.config
        .config("services.riskOrchestrator")
        .property("url")
        .getString()

    val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
    }

    val client = RiskOrchestratorClient(httpClient, riskOrchestratorUrl)

    module(repository, client)
}
