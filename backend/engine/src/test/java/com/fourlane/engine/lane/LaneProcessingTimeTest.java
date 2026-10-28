package com.fourlane.engine.lane;

import com.fourlane.engine.support.FakeClock;
import com.fourlane.engine.support.LaneTestDriver;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.fourlane.engine.lane.LaneFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneProcessingTimeTest {
    @Test void readsClockExactlyOnceAndClampsToPreviousProcessingTime() {
        var calls = new AtomicInteger();
        var clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { calls.incrementAndGet(); return T0.plusSeconds(3); }
        };
        assertEquals(T0.plusSeconds(8), LaneProcessingTime.read(clock, T0.plusSeconds(8)));
        assertEquals(1, calls.get());
        assertEquals(T0.plusSeconds(3), LaneProcessingTime.read(clock, null));
        assertEquals(2, calls.get());
    }

    @Test void rollbackOfWallClockCannotReverseAcceptanceOrDeadline() {
        var d = new LaneTestDriver(bidding(2), T0.plusSeconds(8));
        d.send(firstBid());
        d.clock.set(T0.plusSeconds(4));
        var second = d.send(new LaneCommand.PlaceBid(1, 202, 60000, 70000));
        assertEquals(T0.plusSeconds(8), second.processedAt());
        assertEquals(T0.plusSeconds(8), ((LaneEvent.BidAccepted)second.events().getFirst()).acceptedAt());
        assertEquals(T0.plusSeconds(18), d.state().lot().deadlineAt());

        d.clock.set(T0.plusSeconds(14));
        var before = d.state();
        var rejected = d.send(new LaneCommand.PlaceBid(1, 203, 1, 80000));
        assertSame(before, rejected.nextState());
        assertEquals(T0.plusSeconds(14), d.previousTime());
        d.clock.set(T0.plusSeconds(2));
        var third = d.send(new LaneCommand.PlaceBid(1, 203, 70000, 80000));
        assertEquals(T0.plusSeconds(14), third.processedAt());
        assertEquals(T0.plusSeconds(24), third.nextState().lot().deadlineAt());
    }

    @Test void ignoredTimerCanAdvanceProcessingMetadataWithoutChangingBusinessState() {
        var d = new LaneTestDriver(bidding(1), T0.plusSeconds(20));
        var before = d.state();
        var ignored = d.send(new LaneCommand.CheckDeadline(1, 0));
        assertSame(before, ignored.nextState());
        assertTrue(ignored.effects().isEmpty());
        d.clock.set(T0.plusSeconds(10));
        var bid = d.send(firstBid());
        assertEquals(T0.plusSeconds(20), bid.processedAt());
        assertEquals(BidRejection.LOT_NOT_BIDDING, bid.bidRejection());
        assertSame(before, d.state());
    }

    @Test void fakeClockZoneViewsShareTheSameInstantAndExecutionUsesExpectedJvmZone() {
        var clock = new FakeClock(T0);
        var seoul = clock.withZone(ZoneId.of("Asia/Seoul"));
        clock.advance(Duration.ofMillis(1));
        assertEquals(T0.plusMillis(1), seoul.instant());
        assertEquals(ZoneId.of("Asia/Seoul"), seoul.getZone());
        assertEquals(ZoneId.of(System.getProperty("fourlane.expected-time-zone")), ZoneId.systemDefault());
    }

    @Test void subMillisecondInputIsRejectedInsteadOfRoundingAcrossDeadline() {
        assertThrows(IllegalArgumentException.class,
                () -> LaneProcessingTime.read(Clock.fixed(T0.plusNanos(1), ZoneOffset.UTC), null));
        assertThrows(IllegalArgumentException.class,
                () -> new LaneStateMachine().handle(bidding(1), firstBid(), T0.plusNanos(1)));
    }
}
