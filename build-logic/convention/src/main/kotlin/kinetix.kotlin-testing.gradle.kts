val libs = versionCatalogs.named("libs")

dependencies {
    "testImplementation"(libs.findLibrary("kotest-runner-junit5").get())
    "testImplementation"(libs.findLibrary("kotest-assertions-core").get())
    "testImplementation"(libs.findLibrary("kotest-property").get())
    "testImplementation"(libs.findLibrary("mockk").get())
    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    filter {
        excludeTestsMatching("*IntegrationTest")
        excludeTestsMatching("*End2EndTest")
    }
}

val testSourceSets = the<JavaPluginExtension>().sourceSets

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = testSourceSets["test"].output.classesDirs
    classpath = testSourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("*IntegrationTest")
        isFailOnNoMatchingTests = false
    }
}

val end2EndTest by tasks.registering(Test::class) {
    description = "Runs end-to-end tests."
    group = "verification"
    testClassesDirs = testSourceSets["test"].output.classesDirs
    classpath = testSourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("*End2EndTest")
        isFailOnNoMatchingTests = false
    }
}
