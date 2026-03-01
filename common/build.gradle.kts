plugins {
    id("kinetix.kotlin-library")
    id("kinetix.kotlin-testing")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.kafka.clients)
    compileOnly(libs.logback.classic)
    compileOnly(libs.opentelemetry.sdk.autoconfigure)
    compileOnly(libs.opentelemetry.logback.appender)

    testImplementation(libs.kafka.clients)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.opentelemetry.logback.appender)
    testImplementation(libs.logback.classic)
}
