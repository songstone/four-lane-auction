package com.fourlane.auction.session;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fourlane.auction.session.domain.*;
import com.fourlane.global.error.*;
import com.fourlane.item.domain.ItemAssignmentAccess;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

class SessionLineupFailureIntegrationTest extends SessionLineupTestSupport {
    @Autowired PlatformTransactionManager transactions; @Autowired SessionRepository sessions;
    @MockitoSpyBean ItemAssignmentAccess access;
    private TransactionTemplate tx(boolean rc) { var t=new TransactionTemplate(transactions); if(rc)t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t; }
    @Test void unchangedGuardMatchesOneAndWrongOuterIsolationFailsWithoutMutation() throws Exception {
        long s=create();
        Integer matched = tx(true).execute(st->sessions.reserveLineup(s));
        assertThat(matched).isEqualTo(1);
        assertThatThrownBy(()->tx(false).execute(st->lineup.replace(s,1,List.of()))).isInstanceOf(ApiException.class);
        assertThat(count("lot")).isZero();
    }
    @Test void insertAndBeforeCommitFailureRestoreOriginalLotsAndSessionTimestamp() throws Exception {
        long s=create(), a=item("원래 편성",true), b=item("새 편성",true); lineup.replace(s,1,List.of(a));
        var before=jdbc.queryForList("SELECT * FROM lot"); var sessionBefore=jdbc.queryForMap("SELECT * FROM auction_session WHERE id=?",s);
        jdbc.execute("ALTER TABLE lot ADD CONSTRAINT ck_test_lineup_failure CHECK(item_id<>"+b+")");
        try { assertThat(request("PUT",path(s,1),ids(b)).statusCode()).isEqualTo(500); }
        finally { jdbc.execute("ALTER TABLE lot DROP CHECK ck_test_lineup_failure"); }
        assertThat(jdbc.queryForList("SELECT * FROM lot")).isEqualTo(before);
        assertThatThrownBy(()->tx(true).execute(st->{lineup.replace(s,1,List.of(b));TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void beforeCommit(boolean readOnly){throw new IllegalStateException("beforeCommit test failure");}});return null;})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForList("SELECT * FROM lot")).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT * FROM auction_session WHERE id=?",s)).isEqualTo(sessionBefore);
        lineup.replace(s,1,List.of(b));
    }
    @Test void clockBeforeApprovalFailsAndEqualTimestampRemainsValid() throws Exception {
        long s=create(),a=item("시간 경계",true);when(clock.instant()).thenReturn(BASE.minusMillis(1));
        assertThatThrownBy(()->lineup.replace(s,1,List.of(a))).isInstanceOf(ApiException.class);
        assertThat(count("lot")).isZero();when(clock.instant()).thenReturn(BASE);lineup.replace(s,1,List.of(a));
        assertThat(body(request("GET","/api/me/items",null,"USER")).at("/items/0/stage").asText()).isEqualTo("SCHEDULED");
    }
    @Test void waitsForSessionRowAndRejectsCommittedRunningState() throws Exception {
        long s=create();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var holder=pool.submit(()->tx(false).execute(st->{sessions.lock(s).orElseThrow();jdbc.update("UPDATE auction_session SET status='RUNNING',bid_policy_id=1 WHERE id=?",s);locked.countDown();await(release);return null;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var waiter=pool.submit(()->lineup.replace(s,1,List.of()));
            try { assertThatThrownBy(()->waiter.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); } finally {release.countDown();}
            holder.get(10,TimeUnit.SECONDS);assertThatThrownBy(()->waiter.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class)
                .satisfies(e->assertThat(((ApiException)e.getCause()).getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_SCHEDULED));
        }
    }
    @Test void successfulWaitUsesClockReadAfterItemLock() throws Exception {
        long s=create(),a=item("잠금 뒤 시각",true);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var entered=new CountDownLatch(1);
        doAnswer(inv->{entered.countDown();return inv.callRealMethod();}).when(access).lock(any());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var holder=pool.submit(()->tx(true).execute(st->{jdbc.queryForObject("SELECT id FROM item WHERE id=? FOR UPDATE",Long.class,a);locked.countDown();await(release);return null;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();clearInvocations(clock);
            var waiter=pool.submit(()->lineup.replace(s,1,List.of(a)));
            try {assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->waiter.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);verify(clock,never()).instant();when(clock.instant()).thenReturn(BASE.plusSeconds(9));}
            finally {release.countDown();}
            holder.get(10,TimeUnit.SECONDS);waiter.get(10,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT created_at FROM lot",Timestamp.class).toInstant()).isEqualTo(BASE.plusSeconds(9));
            verify(clock,times(1)).instant();
        }
    }
    @Test void waitingItemValidationSeesCommitAfterEarlierReadAndReadsClockOnlyAfterLock() throws Exception {
        long first=create(),second=create(),a=item("대기 뒤 최신 상태",true);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var entered=new CountDownLatch(1);
        doAnswer(inv->{entered.countDown();return inv.callRealMethod();}).when(access).lock(any());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var holder=pool.submit(()->tx(true).execute(st->{jdbc.queryForObject("SELECT id FROM item WHERE id=? FOR UPDATE",Long.class,a);locked.countDown();await(release);
                jdbc.update("INSERT INTO lot(lane_id,item_id,seq_no,status,start_price,reserve_price,current_price,bid_count,created_at,updated_at) VALUES(?,?,1,'PENDING',50000,917431000,50000,0,?,?)",laneId(first,1),a,Timestamp.from(BASE),Timestamp.from(BASE));return null;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue(); clearInvocations(clock);
            var waiter=pool.submit(()->lineup.replace(second,1,List.of(a)));
            try { assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->waiter.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);verify(clock,never()).instant(); } finally {release.countDown();}
            holder.get(10,TimeUnit.SECONDS); assertThatThrownBy(()->waiter.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class)
                .satisfies(e->assertThat(((ApiException)e.getCause()).getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_ASSIGNABLE));
            assertThat(count("lot")).isEqualTo(1);
        }
    }
    private static void await(CountDownLatch latch) {try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
