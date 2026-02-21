package com.kinetix.loadtest

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.*

class GatewaySimulation : Simulation() {

    private val baseUrl = System.getProperty("baseUrl") ?: "http://localhost:8080"

    private val httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    private val healthCheck = scenario("Health Check Baseline")
        .exec(
            http("GET /health")
                .get("/health")
                .check(status().`is`(200))
        )

    private val portfolioList = scenario("Portfolio List")
        .exec(
            http("GET /api/v1/portfolios")
                .get("/api/v1/portfolios")
                .check(status().`in`(200, 401))
        )

    private val varCalculation = scenario("VaR Calculation")
        .exec(
            http("POST /api/v1/risk/var/port-1")
                .post("/api/v1/risk/var/port-1")
                .body(StringBody("""{"calculationType":"PARAMETRIC","confidenceLevel":"CL_95"}"""))
                .check(status().`in`(200, 401, 404))
        )

    private val mixedWorkload = scenario("Mixed Workload")
        .randomSwitch().on(
            percent(40.0).then(
                exec(
                    http("GET /health")
                        .get("/health")
                        .check(status().`is`(200))
                )
            ),
            percent(30.0).then(
                exec(
                    http("GET /api/v1/portfolios")
                        .get("/api/v1/portfolios")
                        .check(status().`in`(200, 401))
                )
            ),
            percent(20.0).then(
                exec(
                    http("POST VaR")
                        .post("/api/v1/risk/var/port-1")
                        .body(StringBody("""{"calculationType":"PARAMETRIC"}"""))
                        .check(status().`in`(200, 401, 404))
                )
            ),
            percent(10.0).then(
                exec(
                    http("GET /metrics")
                        .get("/metrics")
                        .check(status().`is`(200))
                )
            ),
        )

    init {
        setUp(
            healthCheck.injectOpen(atOnceUsers(10)),
            portfolioList.injectOpen(rampUsers(50).during(30)),
            varCalculation.injectOpen(rampUsers(30).during(30)),
            mixedWorkload.injectOpen(
                rampUsers(100).during(60),
                constantUsersPerSec(20.0).during(120),
            ),
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().percentile3().lt(3000),
                global().successfulRequests().percent().gt(99.0),
            )
    }
}
