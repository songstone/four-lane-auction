package com.fourlane.auction.session;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fourlane.auction.session.application.*;
import com.fourlane.auction.session.domain.SessionRepository;
import com.fourlane.global.error.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
class SessionMutationConcurrencyTest extends SessionTestSupport {
    @Autowired AdminSessionService service;
    @Autowired SessionRepository sessions;
    @Autowired PlatformTransactionManager transactions;
    @Test void disjointPatchesPreserveBothFieldsInFiveHundredRaces()throws Exception{race(false);}
    @Test void sameFieldPatchesReturnEachCommittedValueInFiveHundredRaces()throws Exception{race(true);}
    private void race(boolean same)throws Exception{
        long id=create();
        try(var pool=Executors.newFixedThreadPool(2)){
            for(int i=0;i<500;i++){
                int first=1000+i,second=3000+i;var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
                var a=pool.submit(()->{ready.countDown();assertThat(go.await(10,TimeUnit.SECONDS)).isTrue();return service.patch(id,new AdminSessionService.Input(null,null,Map.of("firstBidWaitMs",first)));});
                var b=pool.submit(()->{ready.countDown();assertThat(go.await(10,TimeUnit.SECONDS)).isTrue();return service.patch(id,new AdminSessionService.Input(null,null,Map.of(same?"firstBidWaitMs":"softCloseMs",second)));});
                assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
                var av=a.get(10,TimeUnit.SECONDS).session().timing();var bv=b.get(10,TimeUnit.SECONDS).session().timing();
                assertThat(av.firstBidWaitMs()).isEqualTo(first);
                if(same){assertThat(bv.firstBidWaitMs()).isEqualTo(second);assertThat(jdbc.queryForObject("SELECT first_bid_wait_ms FROM auction_session WHERE id=?",Integer.class,id)).isIn(first,second);}
                else{assertThat(bv.softCloseMs()).isEqualTo(second);assertThat(jdbc.queryForObject("SELECT first_bid_wait_ms FROM auction_session WHERE id=?",Integer.class,id)).isEqualTo(first);assertThat(jdbc.queryForObject("SELECT soft_close_ms FROM auction_session WHERE id=?",Integer.class,id)).isEqualTo(second);}
            }
        }
    }
    @Test void readsClockAfterWaitingForRowLockAndRechecksFuture()throws Exception{
        long id=create();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var requested=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var holder=pool.submit(()->new TransactionTemplate(transactions).execute(status->{sessions.lock(id).orElseThrow();locked.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return null;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();clearInvocations(clock);
            var waiter=pool.submit(()->{requested.countDown();return service.patch(id,new AdminSessionService.Input(null,BASE.plusMillis(1),Map.of()));});
            try{assertThat(requested.await(10,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->waiter.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);verify(clock,never()).instant();when(clock.instant()).thenReturn(BASE.plusMillis(1));}finally{release.countDown();}
            holder.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->waiter.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class).satisfies(e->assertThat(((ApiException)e.getCause()).getErrorCode()).isEqualTo(ErrorCode.SCHEDULED_START_IN_PAST));
            assertThat(detail(id).at("/session/scheduledStartAt").asText()).isEqualTo("2026-11-02T11:00:00.123Z");
        }
    }
    @Test void waitingPatchObservesStateChangedByAnotherTransaction()throws Exception{
        long id=create();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var holder=pool.submit(()->new TransactionTemplate(transactions).execute(status->{sessions.lock(id).orElseThrow();jdbc.update("UPDATE auction_session SET status='RUNNING',bid_policy_id=1 WHERE id=?",id);locked.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("timeout");}catch(InterruptedException e){throw new AssertionError(e);}return null;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();var waiter=pool.submit(()->service.patch(id,new AdminSessionService.Input(null,null,Map.of("softCloseMs",1234))));
            try{assertThatThrownBy(()->waiter.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            holder.get(10,TimeUnit.SECONDS);assertThatThrownBy(()->waiter.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class).satisfies(e->assertThat(((ApiException)e.getCause()).getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_SCHEDULED));
            assertThat(jdbc.queryForObject("SELECT soft_close_ms FROM auction_session WHERE id=?",Integer.class,id)).isEqualTo(10000);
        }
    }
}
