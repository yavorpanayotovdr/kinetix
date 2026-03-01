package com.kinetix.regulatory.routes

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.dto.FrtbCalculationResponse
import com.kinetix.regulatory.dto.FrtbHistoryResponse
import com.kinetix.regulatory.dto.RiskClassChargeDto
import com.kinetix.regulatory.model.FrtbCalculationRecord
import com.kinetix.regulatory.model.RiskClassCharge
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("com.kinetix.regulatory.routes.RegulatoryRoutes")

fun Route.regulatoryRoutes(
    repository: FrtbCalculationRepository,
    client: RiskOrchestratorClient,
) {
    route("/api/v1/regulatory/frtb/{portfolioId}") {
        route("/calculate") {
            post({
                summary = "Calculate FRTB for a portfolio"
                tags = listOf("FRTB")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                }
            }) {
                val portfolioId = call.parameters["portfolioId"]
                    ?: throw IllegalArgumentException("Missing required path parameter: portfolioId")

                logger.info("FRTB calculation requested for portfolio={}", portfolioId)
                val frtbResult = client.calculateFrtb(portfolioId)

                val record = FrtbCalculationRecord(
                    id = UUID.randomUUID().toString(),
                    portfolioId = portfolioId,
                    totalSbmCharge = frtbResult.totalSbmCharge.toDouble(),
                    grossJtd = frtbResult.grossJtd.toDouble(),
                    hedgeBenefit = frtbResult.hedgeBenefit.toDouble(),
                    netDrc = frtbResult.netDrc.toDouble(),
                    exoticNotional = frtbResult.exoticNotional.toDouble(),
                    otherNotional = frtbResult.otherNotional.toDouble(),
                    totalRrao = frtbResult.totalRrao.toDouble(),
                    totalCapitalCharge = frtbResult.totalCapitalCharge.toDouble(),
                    sbmCharges = frtbResult.sbmCharges.map {
                        RiskClassCharge(
                            riskClass = it.riskClass,
                            deltaCharge = it.deltaCharge.toDouble(),
                            vegaCharge = it.vegaCharge.toDouble(),
                            curvatureCharge = it.curvatureCharge.toDouble(),
                            totalCharge = it.totalCharge.toDouble(),
                        )
                    },
                    calculatedAt = Instant.parse(frtbResult.calculatedAt),
                    storedAt = Instant.now(),
                )

                repository.save(record)
                logger.info("FRTB calculation completed for portfolio={}, totalCapitalCharge={}", portfolioId, record.totalCapitalCharge)
                call.respond(HttpStatusCode.Created, record.toResponse())
            }
        }

        route("/history") {
            get({
                summary = "Get FRTB calculation history"
                tags = listOf("FRTB")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                    queryParameter<String>("limit") {
                        description = "Maximum number of results"
                        required = false
                    }
                    queryParameter<String>("offset") {
                        description = "Number of results to skip"
                        required = false
                    }
                }
            }) {
                val portfolioId = call.parameters["portfolioId"]
                    ?: throw IllegalArgumentException("Missing required path parameter: portfolioId")
                val limit = call.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = call.queryParameters["offset"]?.toIntOrNull() ?: 0

                val records = repository.findByPortfolioId(portfolioId, limit, offset)
                call.respond(
                    FrtbHistoryResponse(
                        calculations = records.map { it.toResponse() },
                        total = records.size,
                        limit = limit,
                        offset = offset,
                    ),
                )
            }
        }

        route("/latest") {
            get({
                summary = "Get latest FRTB calculation"
                tags = listOf("FRTB")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                }
            }) {
                val portfolioId = call.parameters["portfolioId"]
                    ?: throw IllegalArgumentException("Missing required path parameter: portfolioId")

                val record = repository.findLatestByPortfolioId(portfolioId)
                if (record != null) {
                    call.respond(record.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

private fun FrtbCalculationRecord.toResponse() = FrtbCalculationResponse(
    id = id,
    portfolioId = portfolioId,
    sbmCharges = sbmCharges.map {
        RiskClassChargeDto(
            riskClass = it.riskClass,
            deltaCharge = "%.2f".format(it.deltaCharge),
            vegaCharge = "%.2f".format(it.vegaCharge),
            curvatureCharge = "%.2f".format(it.curvatureCharge),
            totalCharge = "%.2f".format(it.totalCharge),
        )
    },
    totalSbmCharge = "%.2f".format(totalSbmCharge),
    grossJtd = "%.2f".format(grossJtd),
    hedgeBenefit = "%.2f".format(hedgeBenefit),
    netDrc = "%.2f".format(netDrc),
    exoticNotional = "%.2f".format(exoticNotional),
    otherNotional = "%.2f".format(otherNotional),
    totalRrao = "%.2f".format(totalRrao),
    totalCapitalCharge = "%.2f".format(totalCapitalCharge),
    calculatedAt = calculatedAt.toString(),
    storedAt = storedAt.toString(),
)
