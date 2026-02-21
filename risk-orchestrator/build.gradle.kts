plugins {
    id("kinetix.kotlin-service")
    id("kinetix.kotlin-testing")
}

application {
    mainClass.set("com.kinetix.risk.ApplicationKt")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":proto"))
    implementation(project(":position-service"))
    implementation(libs.bundles.grpc)
    implementation(libs.grpc.netty)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.database)
    implementation(libs.kafka.clients)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.kafka)
}
