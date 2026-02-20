plugins {
    id("kinetix.kotlin-service")
    id("kinetix.kotlin-testing")
}

application {
    mainClass.set("com.kinetix.audit.ApplicationKt")
}

dependencies {
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.database)
    implementation(libs.kafka.clients)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
}
