plugins {
    id("kinetix.kotlin-service")
    id("kinetix.kotlin-testing")
}

application {
    mainClass.set("com.kinetix.position.ApplicationKt")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.database)

    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
}
