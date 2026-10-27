package com.fourlane.support;

import com.fourlane.FourLaneAuctionApplication;
import java.time.Duration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(classes = FourLaneAuctionApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
// Do not reuse a Spring context whose class-scoped container has stopped.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MySqlIntegrationTest {
    @Container
    // Same MySQL 8.4.13 manifest as Compose; Testcontainers parses the digest-only form.
    protected static final MySQLContainer MYSQL = new MySQLContainer(
            "mysql@sha256:76f0358884cb92d9a086d24e5671c38048d3fef40f2588703d73eb55392d6b69")
            .withDatabaseName("fourlane_test")
            .withUsername("fourlane_test")
            .withPassword("fourlane_test_password")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true")
            .withUrlParam("sslMode", "DISABLED")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withEnv("TZ", "UTC")
            .withCommand("--default-time-zone=+00:00")
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
