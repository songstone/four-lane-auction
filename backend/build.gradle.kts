plugins {
    base
    id("org.springframework.boot") version "4.1.2" apply false
}

apply(from = "gradle/verify-time-source.gradle.kts")

subprojects {
    apply(plugin = "java")

    group = "com.fourlane"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("user.timezone", "UTC")
        systemProperty("fourlane.expected-time-zone", "UTC")
        dependsOn(rootProject.tasks.named("verifyTimeSources"))
    }
}

tasks.named("clean") {
    dependsOn(subprojects.map { "${it.path}:clean" })
}

tasks.named("assemble") {
    dependsOn(subprojects.map { "${it.path}:assemble" })
}

tasks.named("check") {
    dependsOn("verifyTimeSources")
    dependsOn(subprojects.map { "${it.path}:check" })
}
