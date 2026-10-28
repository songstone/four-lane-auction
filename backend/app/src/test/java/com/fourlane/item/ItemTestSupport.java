package com.fourlane.item;

import static org.mockito.Mockito.when;
import com.fourlane.global.security.JwtTokenIssuer;
import com.fourlane.item.application.ItemRegistrationService;
import com.fourlane.support.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
public abstract class ItemTestSupport extends MySqlIntegrationTest {
    protected static final Path ROOT = temporaryRoot();
    protected static final Instant BASE = Instant.parse("2026-10-28T01:02:03.456Z");
    protected static final HttpClient CLIENT = HttpClient.newHttpClient();
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JwtTokenIssuer issuer;
    @Autowired protected ItemRegistrationService registration;
    @MockitoBean protected Clock clock;
    @LocalServerPort protected int port;
    protected long seller;
    protected long other;
    protected long admin;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("fourlane.image-storage.root", ROOT::toString);
    }
    @BeforeEach void prepareItems() throws Exception {
        when(clock.instant()).thenReturn(BASE);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        for (String table : List.of("item_image", "lot", "lane_transition", "lane", "auction_session", "item", "refresh_token", "member_warning", "member"))
            jdbc.update("DELETE FROM " + table);
        try (var files = Files.list(ROOT)) { for (var file : files.toList()) Files.delete(file); }
        seller = member("seller@example.com", "USER");
        other = member("other@example.com", "USER");
        admin = member("admin@example.com", "ADMIN");
    }
    protected long member(String email, String role) {
        jdbc.update("INSERT INTO member(email,password_hash,nickname,role,created_at,updated_at) VALUES(?,?,'출품테스트',?,?,?)",
                email, "test-only-unused-hash", role, Timestamp.from(BASE), Timestamp.from(BASE));
        return jdbc.queryForObject("SELECT id FROM member WHERE email=?", Long.class, email);
    }
    protected String token(long id, String role) { return issuer.issue(id, role, BASE).value(); }
    protected HttpResponse<String> upload(long id, String role, Map<String, String> fields, List<byte[]> images) throws Exception {
        var bytes = ImageFixtures.multipart(fields, images);
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/items"))
                .header("Authorization", "Bearer " + token(id, role))
                .header("Content-Type", "multipart/form-data; boundary=" + ImageFixtures.BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofString());
    }
    protected JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }
    protected long fileCount() throws IOException { try (var files = Files.list(ROOT)) { return files.count(); } }
    protected int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private static Path temporaryRoot() {
        try { return Files.createTempDirectory("fourlane-m1-03-test-"); }
        catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
    @AfterAll static void removeRoot() throws IOException {
        // Only this test-created directory, never a configured application data directory.
        try (var files = Files.list(ROOT)) { for (var file : files.toList()) Files.delete(file); }
        // Shared base root is reused by later test classes in this JVM.
    }
}
