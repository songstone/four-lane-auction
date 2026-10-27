package com.fourlane.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.fourlane.global.security.JwtTokenIssuer;
import com.fourlane.support.MutableClock;
import com.fourlane.support.MySqlIntegrationTest;
import java.lang.reflect.Type;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.messaging.*;
import org.springframework.messaging.support.*;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.user.UserDestinationMessageHandler;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StompAuthenticationIntegrationTest.Observers.class)
class StompAuthenticationIntegrationTest extends MySqlIntegrationTest {
    private static final Instant BASE = Instant.parse("2020-01-01T00:00:00Z");
    @Autowired JwtTokenIssuer issuer;
    @Autowired SimpMessagingTemplate messages;
    @Autowired SubscriptionObserver subscriptions;
    @MockitoBean Clock clock;
    @LocalServerPort int port;
    final MutableClock time = new MutableClock(BASE);
    final List<WebSocketStompClient> clients = new CopyOnWriteArrayList<>();
    ThreadPoolTaskScheduler scheduler;

    @BeforeEach void prepare() {
        time.set(BASE);
        when(clock.instant()).thenAnswer(i -> time.instant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        subscriptions.arrivals.clear();
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
    }

    @AfterEach void cleanup() {
        clients.forEach(WebSocketStompClient::stop);
        scheduler.shutdown();
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN"})
    void validTokenSetsMemberIdAndRole(String role) throws Exception {
        var probe = new Probe();
        var session = connect(probe, token(41, role)).get(10, TimeUnit.SECONDS);
        assertThat(probe.connected.get(10, TimeUnit.SECONDS).getFirst("user-name")).isEqualTo("41");
        subscribe(session, probe, "/user/queue/notifications");
        messages.convertAndSendToUser("41", "/queue/notifications", "private");
        assertThat(probe.received.poll(10, TimeUnit.SECONDS)).isEqualTo("private");
        assertThat(probe.error.isDone()).isFalse();
        session.disconnect();
    }

    @Test void anonymousCannotSubscribeToNotifications() throws Exception {
        var probe = new Probe();
        var session = connect(probe, null).get(10, TimeUnit.SECONDS);
        subscribe(session, probe, "/topic/lanes/12");
        messages.convertAndSend("/topic/lanes/12", "public");
        assertThat(probe.received.poll(10, TimeUnit.SECONDS)).isEqualTo("public");
        session.subscribe("/user/queue/notifications", probe);
        assertRejected(probe, "SUBSCRIPTION_DENIED");
    }

    @Test void anonymousConnectIgnoresAuthenticatedHttpUpgrade() throws Exception {
        var probe = new Probe();
        var session = connect(probe, null, token(41, "USER")).get(10, TimeUnit.SECONDS);
        assertThat(probe.connected.get(10, TimeUnit.SECONDS).getFirst("user-name")).isNull();
        session.subscribe("/user/queue/notifications", probe);
        assertRejected(probe, "SUBSCRIPTION_DENIED");
    }

    @Test void expiredConnectReturnsTokenExpiredAtExactBoundary() throws Exception {
        String value = token(41, "USER");
        time.advance(Duration.ofMinutes(15).minusMillis(1));
        var before = new Probe();
        connect(before, value).get(10, TimeUnit.SECONDS).disconnect();
        time.advance(Duration.ofMillis(1));
        var expired = new Probe();
        connect(expired, value);
        assertRejected(expired, "TOKEN_EXPIRED");
        assertThat(expired.connected.isDone()).isFalse();
        var anonymous = new Probe();
        connect(anonymous, null).get(10, TimeUnit.SECONDS).disconnect();
    }

    @ParameterizedTest @ValueSource(strings = {"", "Bearer ", "Basic value", "Bearer malformed"})
    void invalidConnectReturnsTokenInvalid(String value) throws Exception {
        var probe = new Probe();
        connect(probe, value);
        assertRejected(probe, "TOKEN_INVALID");
        assertThat(probe.connected.isDone()).isFalse();
    }

    @Test void forgedSignatureReturnsTokenInvalid() throws Exception {
        String valid = token(41, "USER");
        String[] parts = valid.substring(7).split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        var probe = new Probe();
        connect(probe, "Bearer " + parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
        assertRejected(probe, "TOKEN_INVALID");
    }

    @Test void connectedSubscriptionSurvivesTokenExpiry() throws Exception {
        String value = token(41, "USER");
        var probe = new Probe();
        var session = connect(probe, value).get(10, TimeUnit.SECONDS);
        subscribe(session, probe, "/user/queue/notifications");
        time.advance(Duration.ofMinutes(16)); // No elapsed-time waiting or production Clock override.
        messages.convertAndSendToUser("41", "/queue/notifications", "after-expiry");
        assertThat(probe.received.poll(10, TimeUnit.SECONDS)).isEqualTo("after-expiry");
        // A new SUBSCRIBE on that same authenticated session also retains its identity.
        subscribe(session, probe, "/topic/lanes/12");
        messages.convertAndSend("/topic/lanes/12", "still-connected");
        assertThat(probe.received.poll(10, TimeUnit.SECONDS)).isEqualTo("still-connected");
        assertThat(session.isConnected()).isTrue();
        assertThat(probe.error.isDone()).isFalse();
        var rejected = new Probe();
        connect(rejected, value);
        assertRejected(rejected, "TOKEN_EXPIRED");
        session.disconnect();
    }

    @Test void concurrentMembersReceiveOnlyOwnMessages() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = new Probe();
            var second = new Probe();
            var one = pool.submit(() -> { start.await(); return connect(first, token(41, "USER")).get(10, TimeUnit.SECONDS); });
            var two = pool.submit(() -> { start.await(); return connect(second, token(42, "USER")).get(10, TimeUnit.SECONDS); });
            start.countDown();
            var a = one.get(15, TimeUnit.SECONDS);
            var b = two.get(15, TimeUnit.SECONDS);
            subscribe(a, first, "/user/queue/notifications");
            subscribe(b, second, "/user/queue/notifications");
            for (int iteration = 0; iteration < 200; iteration++) {
                int round = iteration;
                var gate = new CountDownLatch(1);
                var sendA = pool.submit(() -> { gate.await(); messages.convertAndSendToUser("41", "/queue/notifications", "A-" + round); return true; });
                var sendB = pool.submit(() -> { gate.await(); messages.convertAndSendToUser("42", "/queue/notifications", "B-" + round); return true; });
                gate.countDown();
                sendA.get(10, TimeUnit.SECONDS);
                sendB.get(10, TimeUnit.SECONDS);
                assertThat(first.received.poll(10, TimeUnit.SECONDS)).isEqualTo("A-" + round);
                assertThat(second.received.poll(10, TimeUnit.SECONDS)).isEqualTo("B-" + round);
            }
            assertThat(first.received).isEmpty();
            assertThat(second.received).isEmpty();
            a.disconnect();
            b.disconnect();
        }
    }

    private String token(long id, String role) { return "Bearer " + issuer.issue(id, role, time.instant()).value(); }

    private CompletableFuture<StompSession> connect(Probe probe, String authorization) {
        return connect(probe, authorization, null);
    }

    private CompletableFuture<StompSession> connect(Probe probe, String authorization, String upgradeAuthorization) {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        client.setTaskScheduler(scheduler);
        client.setDefaultHeartbeat(new long[] {10_000, 10_000});
        clients.add(client);
        var handshake = new WebSocketHttpHeaders();
        handshake.setOrigin("http://127.0.0.1:5173");
        if (upgradeAuthorization != null) handshake.set("Authorization", upgradeAuthorization);
        var headers = new StompHeaders();
        headers.setAcceptVersion("1.2");
        if (authorization != null) headers.set("Authorization", authorization);
        return client.connectAsync("ws://127.0.0.1:" + port + "/ws", handshake, headers, probe);
    }

    private void subscribe(StompSession session, Probe probe, String destination) throws Exception {
        String id = UUID.randomUUID().toString();
        var registered = new CompletableFuture<Void>();
        subscriptions.arrivals.put(id, registered);
        var headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setId(id);
        session.subscribe(headers, probe);
        registered.get(10, TimeUnit.SECONDS);
    }

    private void assertRejected(Probe probe, String reason) throws Exception {
        assertThat(probe.error.get(10, TimeUnit.SECONDS)).isEqualTo(reason);
        assertThat(probe.closed.get(10, TimeUnit.SECONDS)).isInstanceOf(ConnectionLostException.class);
    }

    static class Probe extends StompSessionHandlerAdapter {
        final CompletableFuture<StompHeaders> connected = new CompletableFuture<>();
        final CompletableFuture<String> error = new CompletableFuture<>();
        final CompletableFuture<Throwable> closed = new CompletableFuture<>();
        final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        @Override public void afterConnected(StompSession session, StompHeaders headers) { connected.complete(headers); }
        @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) {
            if (headers.getSubscription() == null) error.complete(headers.getFirst("message"));
            else received.add((String) payload);
        }
        @Override public void handleTransportError(StompSession session, Throwable exception) { closed.complete(exception); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Observers {
        @Bean SubscriptionObserver subscriptionObserver() { return new SubscriptionObserver(); }
        @Bean SmartInitializingSingleton brokerRegistrationObserver(
                @Qualifier("clientInboundChannel") AbstractSubscribableChannel channel,
                SubscriptionObserver subscriptions) {
            return () -> channel.addInterceptor(new ExecutorChannelInterceptor() {
                @Override public void afterMessageHandled(Message<?> message, MessageChannel ignored,
                        MessageHandler handler, Exception failure) {
                    var headers = StompHeaderAccessor.wrap(message);
                    if (failure != null || headers.getCommand() != StompCommand.SUBSCRIBE) return;
                    boolean personal = "/user/queue/notifications".equals(headers.getDestination());
                    if ((personal && handler instanceof UserDestinationMessageHandler)
                            || (!personal && handler instanceof SimpleBrokerMessageHandler)) {
                        var arrival = subscriptions.arrivals.remove(headers.getSubscriptionId());
                        if (arrival != null) arrival.complete(null);
                    }
                }
            });
        }
    }
    static class SubscriptionObserver {
        final ConcurrentMap<String, CompletableFuture<Void>> arrivals = new ConcurrentHashMap<>();
    }
}
