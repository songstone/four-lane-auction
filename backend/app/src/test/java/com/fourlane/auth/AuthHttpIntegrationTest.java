package com.fourlane.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fourlane.auth.application.AuthService;
import com.fourlane.auth.domain.RefreshToken;
import com.fourlane.global.security.*;
import com.fourlane.member.application.MemberService;
import com.fourlane.support.*;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class AuthHttpIntegrationTest extends MySqlIntegrationTest {
    private static final Instant BASE = Instant.parse("2026-10-27T00:00:00Z");
    private static final String PASSWORD = "test-only-password-72-boundary";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired MemberService members;
    @Autowired AuthService auth;
    @Autowired JwtTokenIssuer issuer;
    @Autowired JwtConfig jwtConfig;
    @MockitoBean Clock clock;
    @LocalServerPort int port;
    @org.springframework.boot.test.web.server.LocalManagementPort int managementPort;
    final MutableClock time = new MutableClock(BASE);

    @BeforeEach void prepare() {
        time.set(BASE);
        when(clock.instant()).thenAnswer(i -> time.instant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        for (String table : List.of("refresh_token", "member_warning", "member")) jdbc.update("DELETE FROM " + table);
    }

    @Test void signupIgnoresRoleAndAlwaysCreatesUserAndStoresBcrypt() throws Exception {
        var result = signup("Falcon@example.com", PASSWORD, "송골매", ",\"role\":\"ADMIN\"")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.warningCount").value(0))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        long id = body(result).get("memberId").asLong();
        String hash = jdbc.queryForObject("SELECT password_hash FROM member WHERE id=?", String.class, id);
        assertThat(hash.startsWith("{bcrypt}$2")).isTrue();
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT role FROM member WHERE id=?", String.class, id)).isEqualTo("USER");
        signup("falcon@EXAMPLE.COM", PASSWORD, "다른닉", "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
        signup("second@example.com", PASSWORD, "송골매", "").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token", Integer.class)).isZero();
    }

    @Test void loginReturnsExactContractCookieAndOnlyRefreshHashIsStored(CapturedOutput output) throws Exception {
        int logStart = output.getAll().length();
        createUser();
        var result = login("user@EXAMPLE.COM", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTokenExpiresAt").value("2026-10-27T00:15:00.000Z"))
                .andExpect(jsonPath("$.member.nickname").value("테스트"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
        String raw = cookie(result).getValue();
        String access = body(result).get("accessToken").asText();
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800")
                .doesNotContain("Secure");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_token", String.class)
                .equals(RefreshToken.hashOf(raw))).isTrue();
        assertThat(raw.length()).isEqualTo(43);
        assertThat(jdbc.queryForObject("SELECT created_at FROM refresh_token", Timestamp.class).toInstant()).isEqualTo(BASE);
        assertThat(jdbc.queryForObject("SELECT expires_at FROM refresh_token", Timestamp.class).toInstant())
                .isEqualTo(BASE.plus(Duration.ofDays(7)));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
        var rotated = mvc.perform(post("/api/auth/refresh").cookie(cookie(result))).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/auth/refresh").cookie(cookie(result))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").cookie(cookie(rotated))).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(cookie(rotated))).andExpect(status().isUnauthorized());
        login("user@example.com", "wrong-sensitive-password").andExpect(status().isUnauthorized());
        for (String sensitive : List.of(PASSWORD, raw, access, RefreshToken.hashOf(raw),
                cookie(rotated).getValue(), body(rotated).get("accessToken").asText(), "wrong-sensitive-password")) {
            assertThat(output.getAll().substring(logStart).contains(sensitive)).as("captured logs contain sensitive material").isFalse();
        }
    }

    @Test void expiredAccessCanRefreshAfterSixteenMinutesAndOldCookieCannotEraseWinner() throws Exception {
        createUser();
        var before = login("user@example.com", PASSWORD).andReturn();
        String access = body(before).get("accessToken").asText();
        Cookie old = cookie(before);
        time.advance(Duration.ofMinutes(16));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sessions").param("status", "SCHEDULED").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        var after = mvc.perform(post("/api/auth/refresh").cookie(old)).andExpect(status().isOk()).andReturn();
        assertThat(cookie(after).getValue().equals(old.getValue())).isFalse();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + body(after).get("accessToken").asText()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(old)).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(after.getResponse().getHeader("Set-Cookie")).contains("Max-Age=603840");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE expires_at=?", Integer.class,
                Timestamp.from(BASE.plus(Duration.ofDays(7))))).isEqualTo(2);
    }

    @Test void bothTokenTypesRejectAtTheExactExpiryWithoutLeeway() throws Exception {
        createUser();
        var login = login("user@example.com", PASSWORD).andReturn();
        String access = body(login).get("accessToken").asText();
        time.set(BASE.plus(Duration.ofMinutes(15)).minusMillis(1));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        time.advance(Duration.ofMillis(1));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        time.set(BASE.plus(Duration.ofDays(7)).minusMillis(1));
        var last = mvc.perform(post("/api/auth/refresh").cookie(cookie(login))).andExpect(status().isOk()).andReturn();
        time.advance(Duration.ofMillis(1));
        mvc.perform(post("/api/auth/refresh").cookie(cookie(last))).andExpect(status().isUnauthorized());
        time.advance(Duration.ofMillis(1));
        mvc.perform(post("/api/auth/refresh").cookie(cookie(last))).andExpect(status().isUnauthorized());
    }

    @Test void logoutRevokesCookieIsIdempotentAndLeavesIssuedAccessValid() throws Exception {
        createUser();
        var login = login("user@example.com", PASSWORD).andReturn();
        Cookie cookie = cookie(login);
        mvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        var repeated = mvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isNoContent()).andReturn();
        assertThat(repeated.getResponse().getHeader("Set-Cookie"))
                .contains("Max-Age=0", "Path=/api/auth", "HttpOnly", "SameSite=Strict");
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + body(login).get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test void failedReplacementInsertRollsBackRevocation() throws Exception {
        createUser();
        Cookie old = cookie(login("user@example.com", PASSWORD).andReturn());
        jdbc.execute("ALTER TABLE refresh_token ADD CONSTRAINT ck_test_refresh_insert "
                + "CHECK (created_at = '2026-10-27 00:00:00.000')");
        time.advance(Duration.ofSeconds(1));
        try {
            mvc.perform(post("/api/auth/refresh").cookie(old)).andExpect(status().isInternalServerError())
                    .andExpect(header().doesNotExist("Set-Cookie"));
        } finally { jdbc.execute("ALTER TABLE refresh_token DROP CHECK ck_test_refresh_insert"); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL", Integer.class))
                .isEqualTo(1);
        mvc.perform(post("/api/auth/refresh").cookie(old)).andExpect(status().isOk());
    }

    @Test void meReadsActualWarningsAndSuspensionAndAdminSeedingDoesNotPromoteOrOverwrite() throws Exception {
        createUser();
        var user = auth.login("user@example.com", PASSWORD);
        long id = user.member().memberId();
        jdbc.update("INSERT INTO member_warning(member_id,trade_id,created_at) VALUES (?,?,?),(?,?,?)",
                id, 91, Timestamp.from(BASE), id, 92, Timestamp.from(BASE));
        jdbc.update("UPDATE member SET bid_suspended_at=? WHERE id=?", Timestamp.from(BASE), id);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(jsonPath("$.warningCount").value(2)).andExpect(jsonPath("$.bidSuspended").value(true));
        String hash = passwords.encode(PASSWORD);
        assertThatThrownBy(() -> members.seedAdmin("user@example.com", hash, BASE)).isInstanceOf(IllegalStateException.class);
        members.seedAdmin("admin@example.com", hash, BASE);
        members.seedAdmin("ADMIN@example.com", passwords.encode("another-test-password"), BASE);
        var admin = auth.login("admin@example.com", PASSWORD);
        assertThat(admin.member().role().name()).isEqualTo("ADMIN");
        assertThat(admin.member().warningCount()).isZero();
        mvc.perform(get("/api/admin/does-not-exist").header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(post("/api/items").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + admin.accessToken())).andExpect(status().isOk());
        assertThatThrownBy(() -> members.seedAdmin("bad", "bad", BASE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void nonexistentEmailAndWrongPasswordHaveTheSameError() throws Exception {
        createUser();
        login("user@example.com", "wrong-but-long-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        login("missing@example.com", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "wrong-shape", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void absentMalformedAndUnknownRefreshAreUnauthorized(String value) throws Exception {
        var request = post("/api/auth/refresh");
        if (!value.isEmpty()) request.cookie(new Cookie("refreshToken", value));
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test void publicPathsAndManagementRemainPublicWithSecurityEnabled() throws Exception {
        mvc.perform(get("/api/sessions").param("status", "SCHEDULED")).andExpect(status().isOk());
        mvc.perform(get("/api/sessions")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + managementPort + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("status").asText()).isEqualTo("UP");
        for (String path : List.of("/api/sessions?status=SCHEDULED", "/v3/api-docs", "/api/auth/logout")) {
            var builder = path.endsWith("logout") ? post(path) : get(path);
            mvc.perform(builder.header("Authorization", "Bearer forged-value"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        }
    }

    @Test void openApiSeparatesRequestAndResponseAndMatchesRequiredFields() throws Exception {
        var spec = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertThat(spec.at("/paths/~1api~1auth~1signup/post/responses/201/content/application~1json").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1auth~1login/post/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/LoginRequest");
        assertThat(spec.at("/paths/~1api~1auth~1refresh/post/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AuthResponse");
        assertThat(spec.at("/components/schemas/AuthResponse/required").toString()).contains("accessToken", "accessTokenExpiresAt", "member");
        assertThat(spec.at("/components/schemas/MemberResponse/required").size()).isEqualTo(6);
        assertThat(spec.at("/paths/~1api~1auth~1logout/post/responses/204/content").isMissingNode()).isTrue();
        assertThat(spec.at("/paths/~1api~1sessions/get/responses/401/content/application~1json").isMissingNode()).isFalse();
    }

    private void createUser() throws Exception { signup("user@example.com", PASSWORD, "테스트", "").andExpect(status().isCreated()); }
    private ResultActions signup(String email, String password, String nickname, String extra) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType("application/json").content(
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"nickname\":\"" + nickname + "\"" + extra + "}"));
    }
    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json").content(
                json.writeValueAsString(java.util.Map.of("email", email, "password", password))));
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private Cookie cookie(MvcResult result) {
        String first = result.getResponse().getHeader("Set-Cookie").split(";", 2)[0];
        return new Cookie("refreshToken", first.substring("refreshToken=".length()));
    }
}
