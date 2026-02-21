package com.kinetix.loadtest

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.*

class StressTestSimulation : Simulation() {

    private val baseUrl = System.getProperty("baseUrl") ?: "http://localhost:8080"

    private val httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    private val stressScenario = scenario("Stress Test — Ramp to 500 Users")
        .randomSwitch().on(
            percent(50.0).then(
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
                        .body(StringBody("""{"calculationType":"PARAMETRIC","confidenceLevel":"CL_95"}"""))
                        .check(status().`in`(200, 401, 404))
                )
            ),
        )

    init {
        setUp(
            stressScenario.injectOpen(
                rampUsers(500).during(300),
            ),
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().percentile3().lt(3000),
                global().successfulRequests().percent().gt(99.0),
            )
    }
}
