import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot")
}

// Approved M0-02 exception to Boot 4.1.2's Flyway 12.4.0 baseline.
val flywayVersion = "13.11.1"

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(project(":engine"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-core") {
        version { strictly(flywayVersion) }
    }
    runtimeOnly("org.flywaydb:flyway-mysql") {
        version { strictly(flywayVersion) }
    }
    runtimeOnly("com.mysql:mysql-connector-j")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
