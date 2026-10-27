plugins {
    `java-library`
}

// No production dependencies: Spring, JPA and Jackson cannot enter the engine.
dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
