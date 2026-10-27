package com.fourlane.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.fourlane.auth.application.AuthService;
import com.fourlane.auth.domain.*;
import com.fourlane.global.error.ApiException;
import com.fourlane.support.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RefreshRotationConcurrencyTest extends MySqlIntegrationTest {
    static final Instant BASE = Instant.parse("2026-10-27T00:00:00Z");
    @Autowired AuthService auth;
    @Autowired RefreshTokenRepository tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    @LocalServerPort int port;
    final MutableClock time = new MutableClock(BASE);

    @BeforeEach void prepare() {
        time.set(BASE);
        when(clock.instant()).thenAnswer(i -> time.instant());
        for (String table : List.of("refresh_token", "member_warning", "member")) jdbc.update("DELETE FROM " + table);
        auth.signup("race@example.com", "test-only-race-password", "동시성");
    }

    @Test void onlyOneConcurrentRotationCommitsAcrossFiveHundredHttpRaces() throws Exception {
        var first = auth.login("race@example.com", "test-only-race-password");
        String cookie = "refreshToken=" + first.refreshToken();
        var client = HttpClient.newHttpClient();
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 500; round++) {
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                String sameCookie = cookie;
                Callable<HttpResponse<String>> request = () -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("race start timed out");
                    return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/refresh"))
                            .header("Cookie", sameCookie).POST(HttpRequest.BodyPublishers.noBody()).build(),
                            HttpResponse.BodyHandlers.ofString());
                };
                var a = workers.submit(request); var b = workers.submit(request);
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
                var ra = a.get(15, TimeUnit.SECONDS); var rb = b.get(15, TimeUnit.SECONDS);
                assertThat(List.of(ra.statusCode(), rb.statusCode())).containsExactlyInAnyOrder(200, 401);
                var winner = ra.statusCode() == 200 ? ra : rb;
                var loser = ra.statusCode() == 401 ? ra : rb;
                assertThat(loser.headers().firstValue("Set-Cookie")).isEmpty();
                cookie = winner.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
                assertThat(cookie.equals(sameCookie)).isFalse();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL", Integer.class))
                        .isEqualTo(1);
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token", Integer.class)).isEqualTo(501);
        var latest = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/refresh"))
                .header("Cookie", cookie).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(latest.statusCode()).isEqualTo(200);
    }

    @Test void tokenThatExpiresWhileWaitingForTheRowLockIsRejected() throws Exception {
        var issued = auth.login("race@example.com", "test-only-race-password");
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var requested = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                tokens.findForUpdate(RefreshToken.hashOf(issued.refreshToken())).orElseThrow();
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("lock release timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var waiting = workers.submit(() -> { requested.countDown(); return auth.refresh(issued.refreshToken()); });
            try {
                assertThat(requested.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> waiting.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                time.advance(Duration.ofDays(7));
            } finally { release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ApiException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL", Integer.class))
                    .isEqualTo(1); // expired, but neither revoked nor replaced by the rejected request
        }
    }
}
