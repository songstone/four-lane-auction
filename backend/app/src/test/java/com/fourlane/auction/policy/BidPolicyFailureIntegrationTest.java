package com.fourlane.auction.policy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import com.fourlane.auction.policy.application.BidPolicyService;
import com.fourlane.auction.policy.application.BidPolicyService.InputTier;
import com.fourlane.auction.policy.domain.BidPolicyVersionCounterRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

class BidPolicyFailureIntegrationTest extends BidPolicyTestSupport {
    @Autowired BidPolicyService service;
    @Autowired BidPolicyVersionCounterRepository counters;
    @Autowired PlatformTransactionManager transactions;
    @Test void failureBeforeCommitRollsBackTheCounterPolicyAndEveryTier() throws Exception {
        var before = policies(); var beforeTiers = tiers();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            service.create(firstAdmin, List.of(new InputTier(0L, 2000L), new InputTier(10000L, 3000L)));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("test commit failure"); }
            });
            return null;
        })).isInstanceOf(IllegalStateException.class);
        assertThat(counter()).isEqualTo(1); assertThat(policies()).isEqualTo(before); assertThat(tiers()).isEqualTo(beforeTiers);
        assertThat(body(request("POST", VALID, firstAdmin, "ADMIN")).path("version").asInt()).isEqualTo(2);
    }
    @Test void failureDuringTierInsertLeavesNoHalfPolicyAndDoesNotConsumeAVersion() throws Exception {
        var before = policies(); var beforeTiers = tiers();
        jdbc.execute("ALTER TABLE bid_increment_tier ADD CONSTRAINT ck_test_policy_failure CHECK (increment <> 777000)");
        try {
            var response = request("POST", "{\"tiers\":[{\"minPrice\":0,\"increment\":1000},{\"minPrice\":10000,\"increment\":777000}]}", firstAdmin, "ADMIN");
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(body(response).at("/error/code").asText()).isEqualTo("INTERNAL_ERROR");
            assertThat(counter()).isEqualTo(1); assertThat(policies()).isEqualTo(before); assertThat(tiers()).isEqualTo(beforeTiers);
        } finally { jdbc.execute("ALTER TABLE bid_increment_tier DROP CHECK ck_test_policy_failure"); }
        assertThat(body(request("POST", VALID, firstAdmin, "ADMIN")).path("version").asInt()).isEqualTo(2);
    }
    @Test void readsClockOnlyAfterTheCounterLockBecomesAvailable() throws Exception {
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var requested = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                counters.lock().orElseThrow(); locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("counter release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var waiter = pool.submit(() -> { requested.countDown(); return service.create(firstAdmin, List.of(new InputTier(0L, 2000L))); });
            var later = BASE.plus(Duration.ofMinutes(1));
            try {
                assertThat(requested.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> waiter.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                when(clock.instant()).thenReturn(later);
            } finally { release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
            assertThat(waiter.get(10, TimeUnit.SECONDS).createdAt()).isEqualTo(later);
            assertThat(jdbc.queryForObject("SELECT created_at FROM bid_increment_policy WHERE version=2", java.sql.Timestamp.class).toInstant()).isEqualTo(later);
        }
    }
    @Test void versionOverflowFailsWithoutWrappingOrWritingAPolicy() throws Exception {
        jdbc.update("UPDATE bid_policy_version_counter SET last_version=? WHERE id=1", Integer.MAX_VALUE);
        var response = request("POST", VALID, firstAdmin, "ADMIN");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(counter()).isEqualTo(Integer.MAX_VALUE);
        assertThat(policies()).hasSize(1); assertThat(tiers()).hasSize(3);
    }
}
