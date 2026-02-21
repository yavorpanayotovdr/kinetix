plugins {
    id("kinetix.kotlin-service")
    id("kinetix.kotlin-testing")
}

application {
    mainClass.set("com.kinetix.notification.ApplicationKt")
}

dependencies {
    implementation(libs.kafka.clients)
    implementation(libs.kotlinx.serialization.json)
}
