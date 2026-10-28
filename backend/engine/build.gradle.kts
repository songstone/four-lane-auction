plugins {
    `java-library`
}

// No production dependencies: Spring, JPA and Jackson cannot enter the engine.
dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val testSeoul = tasks.register<Test>("testSeoul") {
    description = "Runs the pure engine tests in a separate Asia/Seoul JVM."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("user.timezone", "Asia/Seoul")
    systemProperty("fourlane.expected-time-zone", "Asia/Seoul")
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(testSeoul)
}

// Measure the actual test execution interval, separately from Gradle startup/compilation.
// This is a report for CH-01's proposed five-second target, not a timing-dependent assertion.
tasks.withType<Test>().configureEach {
    val measuredTask = path
    var startedNanos = 0L
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {
            if (suite.parent == null) startedNanos = System.nanoTime()
        }
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                val elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000.0
                logger.lifecycle("Engine test execution: {} elapsedMs={} tests={} failed={} skipped={}",
                    measuredTask, elapsedMs, result.testCount, result.failedTestCount, result.skippedTestCount)
            }
        }
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
    })
}
