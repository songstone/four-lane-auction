package com.fourlane.auction.policy;

import static org.mockito.Mockito.when;
import com.fourlane.global.security.JwtTokenIssuer;
import com.fourlane.support.MySqlIntegrationTest;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BidPolicyTestSupport extends MySqlIntegrationTest {
    protected static final Instant BASE = Instant.parse("2026-10-28T03:04:05.000Z");
    protected static final HttpClient CLIENT = HttpClient.newHttpClient();
    protected static final String VALID = "{\"tiers\":[{\"minPrice\":0,\"increment\":2000},{\"minPrice\":9999,\"increment\":3000}]}";
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JwtTokenIssuer issuer;
    @MockitoBean protected Clock clock;
    @LocalServerPort protected int port;
    protected long firstAdmin, secondAdmin, user;
    @BeforeEach void preparePolicies() {
        when(clock.instant()).thenReturn(BASE);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        jdbc.update("DELETE FROM bid_increment_tier WHERE policy_id <> 1");
        jdbc.update("DELETE FROM bid_increment_policy WHERE id <> 1");
        jdbc.update("UPDATE bid_policy_version_counter SET last_version=1 WHERE id=1");
        jdbc.update("DELETE FROM member");
        firstAdmin = member("policy-first@example.com", "첫운영자", "ADMIN");
        secondAdmin = member("policy-second@example.com", "두운영자", "ADMIN");
        user = member("policy-user@example.com", "일반회원", "USER");
    }
    private long member(String email, String nickname, String role) {
        jdbc.update("INSERT INTO member(email,password_hash,nickname,role,created_at,updated_at) VALUES(?,'test-only-unused-hash',?,?,?,?)",
                email, nickname, role, Timestamp.from(BASE), Timestamp.from(BASE));
        return jdbc.queryForObject("SELECT id FROM member WHERE email=?", Long.class, email);
    }
    protected HttpResponse<String> request(String method, String content, long actor, String role) throws Exception {
        return path(method, "/api/admin/bid-policies", content, actor, role);
    }
    protected HttpResponse<String> path(String method, String path, String content, long actor, String role) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        if (role != null) builder.header("Authorization", "Bearer " + issuer.issue(actor, role, BASE).value());
        if (content != null) builder.header("Content-Type", "application/json");
        return CLIENT.send(builder.method(method, content == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(content)).build(), HttpResponse.BodyHandlers.ofString());
    }
    protected JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }
    protected List<Map<String, Object>> policies() {
        return jdbc.queryForList("SELECT id,version,created_by,created_at FROM bid_increment_policy ORDER BY version");
    }
    protected List<Map<String, Object>> tiers() {
        return jdbc.queryForList("SELECT policy_id,min_price,increment FROM bid_increment_tier ORDER BY policy_id,min_price");
    }
    protected int counter() { return jdbc.queryForObject("SELECT last_version FROM bid_policy_version_counter WHERE id=1", Integer.class); }
}
