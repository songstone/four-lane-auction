package com.fourlane.auction.session;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.global.error.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SessionLineupConcurrencyTest extends SessionLineupTestSupport {
    @Test void crossSessionItemRaceHasExactlyOneWinnerInFiveHundredRounds() throws Exception { race(false); }
    @Test void crossLaneItemRaceHasExactlyOneWinnerInFiveHundredRounds() throws Exception { race(true); }
    private void race(boolean sameSession) throws Exception {
        long first=create(), second=sameSession?first:create(), a=item("동시 편성",true);
        int secondLane=sameSession?2:1;
        try(var pool=Executors.newFixedThreadPool(2)) {
            for(int i=0;i<500;i++) {
                var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
                var x=pool.submit(()->attempt(ready,go,first,1,List.of(a)));
                var y=pool.submit(()->attempt(ready,go,second,secondLane,List.of(a)));
                assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
                assertThat(List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","ITEM_NOT_ASSIGNABLE");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lot WHERE item_id=?",Integer.class,a)).isEqualTo(1);
                lineup.replace(first,1,List.of()); lineup.replace(second,secondLane,List.of());
            }
        }
    }
    @Test void reverseOrderedOverlappingRequestsAndRemovalStayAtomicInFiveHundredRounds() throws Exception {
        long first=create(), second=create(), a=item("중첩 A",true), b=item("중첩 B",true);
        try(var pool=Executors.newFixedThreadPool(2)) {
            for(int i=0;i<500;i++) {
                var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
                var x=pool.submit(()->attempt(ready,go,first,1,List.of(a,b)));
                var y=pool.submit(()->attempt(ready,go,second,1,List.of(b,a)));
                assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
                assertThat(List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","ITEM_NOT_ASSIGNABLE");
                assertThat(count("lot")).isEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT lane_id) FROM lot",Integer.class)).isEqualTo(1);
                lineup.replace(first,1,List.of()); lineup.replace(second,1,List.of());
            }
        }
    }
    @Test void removalRacingWithAssignmentNeverDuplicatesItemInThreeHundredRounds() throws Exception {
        long first=create(),second=create(),a=item("제거 경합",true);
        try(var pool=Executors.newFixedThreadPool(2)) {
            for(int i=0;i<300;i++) {
                lineup.replace(first,1,List.of(a)); var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
                var x=pool.submit(()->attempt(ready,go,first,1,List.of()));
                var y=pool.submit(()->attempt(ready,go,second,1,List.of(a)));
                assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
                assertThat(x.get(20,TimeUnit.SECONDS)).isEqualTo("OK"); assertThat(y.get(20,TimeUnit.SECONDS)).isIn("OK","ITEM_NOT_ASSIGNABLE");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lot WHERE item_id=?",Integer.class,a)).isBetween(0,1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lot WHERE lane_id=?",Integer.class,laneId(first,1))).isZero();
                lineup.replace(second,1,List.of());
            }
        }
    }
    private String attempt(CountDownLatch ready,CountDownLatch go,long session,int lane,List<Long> ids) throws Exception {
        ready.countDown(); if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError("start timeout");
        try { lineup.replace(session,lane,ids); return "OK"; }
        catch(ApiException e) { return e.getErrorCode().name(); }
    }
}
