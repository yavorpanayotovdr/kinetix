plugins {
    id("kinetix.kotlin-service")
    id("kinetix.kotlin-testing")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

tasks.register<Jar>("fatJar") {
    archiveFileName.set("volatility-service.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "io.ktor.server.netty.EngineMain"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}

dependencies {
    implementation(project(":common"))
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.database)
    implementation(libs.kafka.clients)
    implementation(libs.redis.lettuce)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
}
