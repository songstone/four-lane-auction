package com.fourlane.auction.session;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fourlane.FourLaneAuctionApplication;
import com.fourlane.auction.session.application.SessionCursorCodec;
import com.fourlane.auction.session.application.SessionQueryService;
import com.fourlane.auction.session.domain.SessionStatus;
import com.fourlane.support.MySqlIntegrationTest;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(classes = FourLaneAuctionApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(SessionHttpAndStompIntegrationTest.Observers.class)
class SessionHttpAndStompIntegrationTest extends MySqlIntegrationTest {
    private static final Instant BASE = Instant.parse("2026-10-27T00:00:00Z");
    private static final String ORIGIN = "http://127.0.0.1:5173";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired SessionQueryService queries;
    @Autowired SessionCursorCodec cursors;
    @Autowired SubscriptionObserver subscriptions;
    @MockitoBean Clock clock;
    @LocalServerPort int port;
    private final List<WebSocketStompClient> clients = new ArrayList<>();
    private ThreadPoolTaskScheduler heartbeatScheduler;

    @BeforeEach
    void prepare() {
        when(clock.instant()).thenReturn(BASE);
        clearRows();
        subscriptions.destinations.clear();
        heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.initialize();
    }

    @AfterEach
    void cleanup() {
        clients.forEach(WebSocketStompClient::stop);
        heartbeatScheduler.shutdown();
        clearRows();
    }

    private void clearRows() {
        for (String table : List.of("lot", "lane", "auction_session", "item", "member")) {
            jdbc.update("DELETE FROM " + table); // Fixed test-only table names; never user input.
        }
    }

    @Test
    void realEmptyTableAndClockProduceTheExactPublicResponse() throws Exception {
        mvc.perform(get("/api/sessions").param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"serverTime":"2026-10-27T00:00:00.000Z","items":[],"nextCursor":null}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @ParameterizedTest
    @EnumSource(SessionStatus.class)
    void pagesByStatusSpecificTimeAndIdWithoutLosingTies(SessionStatus state) {
        session(101, state, 0);
        session(102, state, 0);
        session(103, state, 10);
        session(104, state, 20);
        session(105, state, 10);
        session(999, state == SessionStatus.SCHEDULED ? SessionStatus.RUNNING : SessionStatus.SCHEDULED, 0);
        var seen = new ArrayList<Long>();
        String cursor = null;
        int pages = 0;
        do {
            var page = queries.list(state, 2, cursor);
            seen.addAll(page.items().stream().map(row -> row.getSessionId()).toList());
            cursor = page.nextCursor();
            assertThat(++pages).isLessThanOrEqualTo(3);
        } while (cursor != null);
        assertThat(seen).containsExactlyElementsOf(state == SessionStatus.SCHEDULED
                ? List.of(101L, 102L, 103L, 105L, 104L) : List.of(104L, 105L, 103L, 102L, 101L));
    }

    @Test
    void pageContinuesAfterAnchorRowWasDeletedAndAnEarlierRowWasInserted() {
        for (long id = 101; id <= 104; id++) session(id, SessionStatus.SCHEDULED, (int) id);
        var first = queries.list(SessionStatus.SCHEDULED, 2, null);
        jdbc.update("DELETE FROM auction_session WHERE id = 102");
        session(100, SessionStatus.SCHEDULED, 0);
        assertThat(queries.list(SessionStatus.SCHEDULED, 2, first.nextCursor()).items())
                .extracting(row -> row.getSessionId()).containsExactly(103L, 104L);
    }

    @Test
    void usesDefaultTwentyAndSupportsMaximumHundred() throws Exception {
        for (long id = 1; id <= 25; id++) session(id, SessionStatus.SCHEDULED, (int) id);
        mvc.perform(get("/api/sessions").param("status", "SCHEDULED"))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.nextCursor").isString());
        mvc.perform(get("/api/sessions").param("status", "SCHEDULED").param("size", "100"))
                .andExpect(jsonPath("$.items.length()").value(25))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void countsLotsAcrossAllLanesAndKeepsAllNullableFields() throws Exception {
        session(1, SessionStatus.SCHEDULED, 0);
        var stamp = Timestamp.from(BASE);
        jdbc.update("""
                INSERT INTO member(id,email,password_hash,nickname,role,created_at,updated_at)
                VALUES (1,'fixture@example.invalid','unused','검증','USER',?,?)
                """, stamp, stamp);
        jdbc.update("""
                INSERT INTO item(id,seller_id,name,start_price,reserve_price,review_status,created_at,updated_at)
                VALUES (1,1,'검증 물품',1000,1000,'APPROVED',?,?)
                """, stamp, stamp);
        for (int lane = 1; lane <= 2; lane++) {
            jdbc.update("""
                    INSERT INTO lane(id,session_id,lane_no,status,created_at,updated_at)
                    VALUES (?,1,?,'READY',?,?)
                    """, lane, lane, stamp, stamp);
        }
        for (int id = 1; id <= 3; id++) {
            jdbc.update("""
                    INSERT INTO lot(id,lane_id,item_id,seq_no,status,start_price,reserve_price,
                        current_price,bid_count,created_at,updated_at)
                    VALUES (?,?,1,?,'PENDING',1000,1000,1000,0,?,?)
                    """, id, id == 3 ? 2 : 1, id, stamp, stamp);
        }
        mvc.perform(get("/api/sessions").param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"serverTime":"2026-10-27T00:00:00.000Z","items":[{
                          "sessionId":1,"status":"SCHEDULED","laneCount":2,
                          "scheduledStartAt":"2026-10-27T00:00:00.000Z",
                          "startedAt":null,"finishedAt":null,"lotCount":3}],"nextCursor":null}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "broken", "%%%%", "MXxTQ0hFRFVMRUR8fDA"})
    void malformedCursorIsAFieldError(String cursor) throws Exception {
        mvc.perform(get("/api/sessions").param("status", "SCHEDULED").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fields[0].field").value("cursor"));
    }

    @Test
    void cursorCannotBeReusedForAnotherStatus() throws Exception {
        mvc.perform(get("/api/sessions").param("status", "SCHEDULED")
                        .param("cursor", cursors.encode(SessionStatus.RUNNING, BASE, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openApiMatchesRequiredParametersAndRequiredNullableProperties() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/sessions'].get.operationId").value("listSessions"))
                .andExpect(jsonPath("$.paths['/api/sessions'].get.parameters[?(@.name=='status')].required",
                        hasItem(true)))
                .andExpect(jsonPath("$.paths['/api/sessions'].get.parameters[?(@.name=='size')].schema.default",
                        hasItem(20)))
                .andExpect(jsonPath("$.components.schemas.SessionStatus.enum",
                        containsInAnyOrder("SCHEDULED", "RUNNING", "FINISHED")))
                .andExpect(jsonPath("$.components.schemas.SessionListResponse.required",
                        containsInAnyOrder("serverTime", "items", "nextCursor")))
                .andExpect(jsonPath("$.components.schemas.SessionListResponse.properties.nextCursor.type",
                        containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.SessionSummary.required",
                        containsInAnyOrder("sessionId", "status", "laneCount", "scheduledStartAt",
                                "startedAt", "finishedAt", "lotCount")))
                .andExpect(jsonPath("$.components.schemas.SessionSummary.properties.startedAt.type",
                        containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.SessionSummary.properties.finishedAt.type",
                        containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.paths['/api/sessions'].get.responses['400']").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.required", contains("error")))
                .andExpect(jsonPath("$.components.schemas.FieldViolation.required",
                        containsInAnyOrder("field", "reason")));
    }

    @Test
    void anonymousConnectNegotiatesStomp12AndTenSecondHeartbeatsAndAllowsLaneSubscription() throws Exception {
        var probe = new Probe();
        var session = connect(probe, new StompHeaders(), ORIGIN).get(10, TimeUnit.SECONDS);
        assertThat(probe.connected.get(10, TimeUnit.SECONDS).getFirst("version")).isEqualTo("1.2");
        assertThat(probe.connected.join().getHeartbeat()).containsExactly(10_000, 10_000);
        assertThat(probe.connected.join().getFirst("user-name")).isNull();
        var headers = new StompHeaders();
        headers.setDestination("/topic/lanes/12");
        headers.setId("lane-12");
        session.subscribe(headers, probe);
        assertThat(subscriptions.destinations.poll(10, TimeUnit.SECONDS)).isEqualTo("/topic/lanes/12");
        session.disconnect();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/queue/private", "/user/queue/notifications", "/topic/ping", "/topic/lanes/12/extra"})
    void forbiddenSubscriptionGetsErrorAndConnectionClosed(String destination) throws Exception {
        var probe = new Probe();
        var session = connect(probe, new StompHeaders(), ORIGIN).get(10, TimeUnit.SECONDS);
        session.subscribe(destination, probe);
        assertThat(probe.error.get(10, TimeUnit.SECONDS)).isEqualTo("SUBSCRIPTION_DENIED");
        assertThat(probe.closed.get(10, TimeUnit.SECONDS)).isInstanceOf(ConnectionLostException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/lanes/12", "/queue/private", "/app/anything"})
    void sendIsDeniedForEveryDestination(String destination) throws Exception {
        var probe = new Probe();
        var session = connect(probe, new StompHeaders(), ORIGIN).get(10, TimeUnit.SECONDS);
        session.send(destination, "not-accepted");
        assertThat(probe.error.get(10, TimeUnit.SECONDS)).isEqualTo("SEND_DENIED");
        assertThat(probe.closed.get(10, TimeUnit.SECONDS)).isInstanceOf(ConnectionLostException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer not-verified-yet"})
    void tokenConnectIsRejectedUntilM102(String value) throws Exception {
        var probe = new Probe();
        var headers = new StompHeaders();
        headers.set("Authorization", value);
        connect(probe, headers, ORIGIN);
        assertThat(probe.error.get(10, TimeUnit.SECONDS)).isEqualTo("TOKEN_INVALID");
        assertThat(probe.closed.get(10, TimeUnit.SECONDS)).isInstanceOf(ConnectionLostException.class);
        assertThat(probe.connected.isDone()).isFalse();
    }

    @Test
    void unlistedOriginCannotUpgrade() {
        assertThatThrownBy(() -> connect(new Probe(), new StompHeaders(), "https://unlisted.example")
                .get(10, TimeUnit.SECONDS)).hasStackTraceContaining("403");
    }

    private CompletableFuture<StompSession> connect(Probe probe, StompHeaders headers, String origin) {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        client.setTaskScheduler(heartbeatScheduler);
        client.setDefaultHeartbeat(new long[] {10_000, 10_000});
        clients.add(client);
        var handshake = new WebSocketHttpHeaders();
        handshake.setOrigin(origin);
        headers.setAcceptVersion("1.2");
        return client.connectAsync("ws://127.0.0.1:" + port + "/ws", handshake, headers, probe);
    }

    private void session(long id, SessionStatus state, int offset) {
        var time = Timestamp.from(BASE.plusSeconds(offset));
        jdbc.update("""
                INSERT INTO auction_session(id,status,lane_count,scheduled_start_at,first_bid_wait_ms,
                    soft_close_ms,lot_gap_ms,resume_min_ms,bid_policy_id,started_at,finished_at,created_at,updated_at)
                VALUES (?,?,2,?,15000,10000,5000,10000,?,?,?,?,?)
                """, id, state.name(), time, state == SessionStatus.SCHEDULED ? null : 1,
                state == SessionStatus.SCHEDULED ? null : time,
                state == SessionStatus.FINISHED ? time : null, time, time);
    }

    static class Probe extends StompSessionHandlerAdapter {
        final CompletableFuture<StompHeaders> connected = new CompletableFuture<>();
        final CompletableFuture<String> error = new CompletableFuture<>();
        final CompletableFuture<Throwable> closed = new CompletableFuture<>();

        @Override public void afterConnected(StompSession session, StompHeaders headers) { connected.complete(headers); }
        @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) { error.complete(headers.getFirst("message")); }
        @Override public void handleTransportError(StompSession session, Throwable exception) { closed.complete(exception); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Observers {
        @Bean SubscriptionObserver subscriptionObserver() { return new SubscriptionObserver(); }
    }

    static class SubscriptionObserver {
        final LinkedBlockingQueue<String> destinations = new LinkedBlockingQueue<>();
        @EventListener
        public void subscribed(SessionSubscribeEvent event) {
            destinations.add(StompHeaderAccessor.wrap(event.getMessage()).getDestination());
        }
    }
}
