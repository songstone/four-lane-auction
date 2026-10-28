package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import com.fourlane.item.application.ItemReviewService;
import com.fourlane.item.domain.ItemRepository;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

class ItemReviewFailureIntegrationTest extends ItemReviewTestSupport {
    @Autowired ItemReviewService reviews;
    @Autowired ItemRepository items;
    @Autowired PlatformTransactionManager transactions;
    @Test void failureBeforeCommitRollsBackAllReviewFields() throws Exception {
        long id = newItem("롤백 검사");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            reviews.reject(id, "확인 불가");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("test commit failure"); }
            });
            return null;
        })).isInstanceOf(IllegalStateException.class);
        var row = jdbc.queryForMap("SELECT review_status,reject_reason,reviewed_at,updated_at,created_at FROM item WHERE id=?", id);
        assertThat(row.get("review_status")).isEqualTo("PENDING_REVIEW");
        assertThat(row.get("reject_reason")).isNull(); assertThat(row.get("reviewed_at")).isNull();
        assertThat(row.get("updated_at")).isEqualTo(row.get("created_at"));
        assertThat(review(id, "approve").statusCode()).isEqualTo(200);
    }
    @Test void reviewReadsClockOnlyAfterAcquiringTheItemLock() throws Exception {
        long id = newItem("락 대기 시각");
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var requested = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                items.findForUpdate(id).orElseThrow(); locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var waiter = pool.submit(() -> { requested.countDown(); return reviews.approve(id); });
            var later = BASE.plus(Duration.ofMinutes(1));
            try {
                assertThat(requested.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> waiter.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                when(clock.instant()).thenReturn(later);
            } finally { release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
            assertThat(waiter.get(10, TimeUnit.SECONDS).reviewedAt()).isEqualTo(later);
        }
    }
}
