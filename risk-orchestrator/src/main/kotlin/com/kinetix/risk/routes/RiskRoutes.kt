package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.cache.LatestVaRCache
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.mapper.*
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import com.kinetix.risk.service.VaRCalculationService
import com.kinetix.proto.common.PortfolioId as ProtoPortfolioId
import com.kinetix.proto.risk.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant

// --- Response DTOs matching gateway's expected shapes ---

@Serializable
data class ComponentBreakdownDto(
    val assetClass: String,
    val varContribution: String,
    val percentageOfTotal: String,
)

@Serializable
data class VaRResultResponse(
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: String,
    val expectedShortfall: String,
    val componentBreakdown: List<ComponentBreakdownDto>,
    val calculatedAt: String,
)

@Serializable
data class VaRCalculationRequestBody(
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
    val numSimulations: String? = null,
)

@Serializable
data class AssetClassImpactDto(
    val assetClass: String,
    val baseExposure: String,
    val stressedExposure: String,
    val pnlImpact: String,
)

@Serializable
data class StressTestResponse(
    val scenarioName: String,
    val baseVar: String,
    val stressedVar: String,
    val pnlImpact: String,
    val assetClassImpacts: List<AssetClassImpactDto>,
    val calculatedAt: String,
)

@Serializable
data class StressTestRequestBody(
    val scenarioName: String,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
    val volShocks: Map<String, Double>? = null,
    val priceShocks: Map<String, Double>? = null,
    val description: String? = null,
)

@Serializable
data class GreekValuesDto(
    val assetClass: String,
    val delta: String,
    val gamma: String,
    val vega: String,
)

@Serializable
data class GreeksResponse(
    val portfolioId: String,
    val assetClassGreeks: List<GreekValuesDto>,
    val theta: String,
    val rho: String,
    val calculatedAt: String,
)

@Serializable
data class RiskClassChargeDto(
    val riskClass: String,
    val deltaCharge: String,
    val vegaCharge: String,
    val curvatureCharge: String,
    val totalCharge: String,
)

@Serializable
data class FrtbResultResponse(
    val portfolioId: String,
    val sbmCharges: List<RiskClassChargeDto>,
    val totalSbmCharge: String,
    val grossJtd: String,
    val hedgeBenefit: String,
    val netDrc: String,
    val exoticNotional: String,
    val otherNotional: String,
    val totalRrao: String,
    val totalCapitalCharge: String,
    val calculatedAt: String,
)

@Serializable
data class GenerateReportRequestBody(val format: String? = null)

@Serializable
data class ReportResponse(
    val portfolioId: String,
    val format: String,
    val content: String,
    val generatedAt: String,
)

@Serializable
data class DependenciesRequestBody(
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
)

@Serializable
data class MarketDataDependencyDto(
    val dataType: String,
    val instrumentId: String,
    val assetClass: String,
    val required: Boolean,
    val description: String,
    val parameters: Map<String, String>,
)

@Serializable
data class DataDependenciesResponse(
    val portfolioId: String,
    val dependencies: List<MarketDataDependencyDto>,
)

@Serializable
data class ErrorResponse(val error: String, val message: String)

// --- Mappers ---

private fun VaRResult.toResponse() = VaRResultResponse(
    portfolioId = portfolioId.value,
    calculationType = calculationType.name,
    confidenceLevel = confidenceLevel.name,
    varValue = "%.2f".format(varValue),
    expectedShortfall = "%.2f".format(expectedShortfall),
    componentBreakdown = componentBreakdown.map {
        ComponentBreakdownDto(
            assetClass = it.assetClass.name,
            varContribution = "%.2f".format(it.varContribution),
            percentageOfTotal = "%.2f".format(it.percentageOfTotal),
        )
    },
    calculatedAt = calculatedAt.toString(),
)

private val FRTB_RISK_CLASS_NAMES = mapOf(
    FrtbRiskClass.GIRR to "GIRR",
    FrtbRiskClass.CSR_NON_SEC to "CSR_NON_SEC",
    FrtbRiskClass.CSR_SEC_CTP to "CSR_SEC_CTP",
    FrtbRiskClass.CSR_SEC_NON_CTP to "CSR_SEC_NON_CTP",
    FrtbRiskClass.FRTB_EQUITY to "EQUITY",
    FrtbRiskClass.FRTB_COMMODITY to "COMMODITY",
    FrtbRiskClass.FRTB_FX to "FX",
)

private val ASSET_CLASS_TO_PROTO = mapOf(
    AssetClass.EQUITY to com.kinetix.proto.common.AssetClass.EQUITY,
    AssetClass.FIXED_INCOME to com.kinetix.proto.common.AssetClass.FIXED_INCOME,
    AssetClass.FX to com.kinetix.proto.common.AssetClass.FX,
    AssetClass.COMMODITY to com.kinetix.proto.common.AssetClass.COMMODITY,
    AssetClass.DERIVATIVE to com.kinetix.proto.common.AssetClass.DERIVATIVE,
)

private val PROTO_ASSET_CLASS_TO_DOMAIN = ASSET_CLASS_TO_PROTO.entries.associate { (k, v) -> v to k }

// --- Routes ---

private val MARKET_DATA_TYPE_NAMES = mapOf(
    MarketDataType.SPOT_PRICE to "SPOT_PRICE",
    MarketDataType.HISTORICAL_PRICES to "HISTORICAL_PRICES",
    MarketDataType.VOLATILITY_SURFACE to "VOLATILITY_SURFACE",
    MarketDataType.YIELD_CURVE to "YIELD_CURVE",
    MarketDataType.RISK_FREE_RATE to "RISK_FREE_RATE",
    MarketDataType.DIVIDEND_YIELD to "DIVIDEND_YIELD",
    MarketDataType.CREDIT_SPREAD to "CREDIT_SPREAD",
    MarketDataType.FORWARD_CURVE to "FORWARD_CURVE",
    MarketDataType.CORRELATION_MATRIX to "CORRELATION_MATRIX",
)

fun Route.riskRoutes(
    varCalculationService: VaRCalculationService,
    varCache: LatestVaRCache,
    positionProvider: PositionProvider,
    stressTestStub: StressTestServiceGrpcKt.StressTestServiceCoroutineStub,
    regulatoryStub: RegulatoryReportingServiceGrpcKt.RegulatoryReportingServiceCoroutineStub,
    riskEngineClient: RiskEngineClient? = null,
) {
    // VaR routes
    route("/api/v1/risk/var/{portfolioId}") {
        post {
            val portfolioId = call.requirePathParam("portfolioId")
            val body = call.receive<VaRCalculationRequestBody>()
            val request = VaRCalculationRequest(
                portfolioId = PortfolioId(portfolioId),
                calculationType = CalculationType.valueOf(body.calculationType ?: "PARAMETRIC"),
                confidenceLevel = ConfidenceLevel.valueOf(body.confidenceLevel ?: "CL_95"),
                timeHorizonDays = body.timeHorizonDays?.toInt() ?: 1,
                numSimulations = body.numSimulations?.toInt() ?: 10_000,
            )
            val result = varCalculationService.calculateVaR(request)
            if (result != null) {
                varCache.put(portfolioId, result)
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get {
            val portfolioId = call.requirePathParam("portfolioId")
            val cached = varCache.get(portfolioId)
            if (cached != null) {
                call.respond(cached.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    // Stress test routes
    route("/api/v1/risk/stress/{portfolioId}") {
        post {
            val portfolioId = call.requirePathParam("portfolioId")
            val body = call.receive<StressTestRequestBody>()
            val positions = positionProvider.getPositions(PortfolioId(portfolioId))
            val calcType = CalculationType.valueOf(body.calculationType ?: "PARAMETRIC")
            val confLevel = ConfidenceLevel.valueOf(body.confidenceLevel ?: "CL_95")

            val protoRequest = StressTestRequest.newBuilder()
                .setPortfolioId(ProtoPortfolioId.newBuilder().setValue(portfolioId))
                .setScenarioName(body.scenarioName)
                .setCalculationType(calcType.toProto())
                .setConfidenceLevel(confLevel.toProto())
                .setTimeHorizonDays(body.timeHorizonDays?.toInt() ?: 1)
                .addAllPositions(positions.map { it.toProto() })
                .also { builder ->
                    body.volShocks?.forEach { (k, v) -> builder.putVolShocks(k, v) }
                    body.priceShocks?.forEach { (k, v) -> builder.putPriceShocks(k, v) }
                    body.description?.let { builder.description = it }
                }
                .build()

            val response = stressTestStub.runStressTest(protoRequest)
            call.respond(
                StressTestResponse(
                    scenarioName = response.scenarioName,
                    baseVar = "%.2f".format(response.baseVar),
                    stressedVar = "%.2f".format(response.stressedVar),
                    pnlImpact = "%.2f".format(response.pnlImpact),
                    assetClassImpacts = response.assetClassImpactsList.map {
                        AssetClassImpactDto(
                            assetClass = (PROTO_ASSET_CLASS_TO_DOMAIN[it.assetClass] ?: AssetClass.EQUITY).name,
                            baseExposure = "%.2f".format(it.baseExposure),
                            stressedExposure = "%.2f".format(it.stressedExposure),
                            pnlImpact = "%.2f".format(it.pnlImpact),
                        )
                    },
                    calculatedAt = Instant.ofEpochSecond(response.calculatedAt.seconds, response.calculatedAt.nanos.toLong()).toString(),
                )
            )
        }
    }

    get("/api/v1/risk/stress/scenarios") {
        val response = stressTestStub.listScenarios(ListScenariosRequest.getDefaultInstance())
        call.respond(response.scenarioNamesList)
    }

    // Greeks routes
    route("/api/v1/risk/greeks/{portfolioId}") {
        post {
            val portfolioId = call.requirePathParam("portfolioId")
            val body = call.receive<VaRCalculationRequestBody>()
            val positions = positionProvider.getPositions(PortfolioId(portfolioId))
            val calcType = CalculationType.valueOf(body.calculationType ?: "PARAMETRIC")
            val confLevel = ConfidenceLevel.valueOf(body.confidenceLevel ?: "CL_95")

            val protoRequest = GreeksRequest.newBuilder()
                .setPortfolioId(ProtoPortfolioId.newBuilder().setValue(portfolioId))
                .setCalculationType(calcType.toProto())
                .setConfidenceLevel(confLevel.toProto())
                .setTimeHorizonDays(body.timeHorizonDays?.toInt() ?: 1)
                .addAllPositions(positions.map { it.toProto() })
                .build()

            val response = stressTestStub.calculateGreeks(protoRequest)
            call.respond(
                GreeksResponse(
                    portfolioId = response.portfolioId,
                    assetClassGreeks = response.assetClassGreeksList.map {
                        GreekValuesDto(
                            assetClass = (PROTO_ASSET_CLASS_TO_DOMAIN[it.assetClass] ?: AssetClass.EQUITY).name,
                            delta = "%.6f".format(it.delta),
                            gamma = "%.6f".format(it.gamma),
                            vega = "%.6f".format(it.vega),
                        )
                    },
                    theta = "%.6f".format(response.theta),
                    rho = "%.6f".format(response.rho),
                    calculatedAt = Instant.ofEpochSecond(response.calculatedAt.seconds, response.calculatedAt.nanos.toLong()).toString(),
                )
            )
        }
    }

    // FRTB routes
    route("/api/v1/regulatory/frtb/{portfolioId}") {
        post {
            val portfolioId = call.requirePathParam("portfolioId")
            val positions = positionProvider.getPositions(PortfolioId(portfolioId))

            val protoRequest = FrtbRequest.newBuilder()
                .setPortfolioId(ProtoPortfolioId.newBuilder().setValue(portfolioId))
                .addAllPositions(positions.map { it.toProto() })
                .build()

            val response = regulatoryStub.calculateFrtb(protoRequest)
            call.respond(
                FrtbResultResponse(
                    portfolioId = response.portfolioId,
                    sbmCharges = response.sbm.riskClassChargesList.map {
                        RiskClassChargeDto(
                            riskClass = FRTB_RISK_CLASS_NAMES[it.riskClass] ?: it.riskClass.name,
                            deltaCharge = "%.2f".format(it.deltaCharge),
                            vegaCharge = "%.2f".format(it.vegaCharge),
                            curvatureCharge = "%.2f".format(it.curvatureCharge),
                            totalCharge = "%.2f".format(it.totalCharge),
                        )
                    },
                    totalSbmCharge = "%.2f".format(response.sbm.totalSbmCharge),
                    grossJtd = "%.2f".format(response.drc.grossJtd),
                    hedgeBenefit = "%.2f".format(response.drc.hedgeBenefit),
                    netDrc = "%.2f".format(response.drc.netDrc),
                    exoticNotional = "%.2f".format(response.rrao.exoticNotional),
                    otherNotional = "%.2f".format(response.rrao.otherNotional),
                    totalRrao = "%.2f".format(response.rrao.totalRrao),
                    totalCapitalCharge = "%.2f".format(response.totalCapitalCharge),
                    calculatedAt = Instant.ofEpochSecond(response.calculatedAt.seconds, response.calculatedAt.nanos.toLong()).toString(),
                )
            )
        }
    }

    // Report routes
    route("/api/v1/regulatory/report/{portfolioId}") {
        post {
            val portfolioId = call.requirePathParam("portfolioId")
            val body = call.receive<GenerateReportRequestBody>()
            val positions = positionProvider.getPositions(PortfolioId(portfolioId))
            val format = when (body.format?.uppercase()) {
                "XBRL" -> ReportFormat.XBRL
                else -> ReportFormat.CSV
            }

            val protoRequest = GenerateReportRequest.newBuilder()
                .setPortfolioId(ProtoPortfolioId.newBuilder().setValue(portfolioId))
                .addAllPositions(positions.map { it.toProto() })
                .setFormat(format)
                .build()

            val response = regulatoryStub.generateReport(protoRequest)
            call.respond(
                ReportResponse(
                    portfolioId = response.portfolioId,
                    format = response.format.name,
                    content = response.content,
                    generatedAt = Instant.ofEpochSecond(response.generatedAt.seconds, response.generatedAt.nanos.toLong()).toString(),
                )
            )
        }
    }

    // Market data dependencies routes
    if (riskEngineClient != null) {
        route("/api/v1/risk/dependencies/{portfolioId}") {
            post {
                val portfolioId = call.requirePathParam("portfolioId")
                val body = call.receive<DependenciesRequestBody>()
                val positions = positionProvider.getPositions(PortfolioId(portfolioId))
                val calcType = body.calculationType ?: "PARAMETRIC"
                val confLevel = body.confidenceLevel ?: "CL_95"

                val response = riskEngineClient.discoverDependencies(positions, calcType, confLevel)
                call.respond(
                    DataDependenciesResponse(
                        portfolioId = portfolioId,
                        dependencies = response.dependenciesList.map {
                            MarketDataDependencyDto(
                                dataType = MARKET_DATA_TYPE_NAMES[it.dataType] ?: it.dataType.name,
                                instrumentId = it.instrumentId,
                                assetClass = it.assetClass,
                                required = it.required,
                                description = it.description,
                                parameters = it.parametersMap,
                            )
                        },
                    )
                )
            }
        }
    }
}
