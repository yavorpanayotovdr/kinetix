plugins {
    id("kinetix.kotlin-common")
    id("kinetix.kotlin-testing")
}

tasks.named<Test>("test") {
    filter {
        isFailOnNoMatchingTests = false
    }
}

dependencies {
    testImplementation(project(":common"))
    testImplementation(project(":position-service"))
    testImplementation(project(":audit-service"))
    testImplementation(project(":risk-orchestrator"))
    testImplementation(libs.bundles.exposed)
    testImplementation(libs.bundles.database)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.kafka.clients)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.micrometer.prometheus)
}
