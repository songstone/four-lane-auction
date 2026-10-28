package com.fourlane.engine.lane;

import com.fourlane.engine.support.LaneTestDriver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static com.fourlane.engine.lane.LaneFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneTimingTest {
    @ParameterizedTest
    @CsvSource({"2000,15000", "8000,18000"})
    void acceptedBidNeverShortensDeadline(long bidAt, long deadlineAt) {
        var d = new LaneTestDriver(bidding(1), T0.plusMillis(bidAt));
        var result = d.send(firstBid());
        assertEquals(T0.plusMillis(deadlineAt), result.nextState().lot().deadlineAt());
        assertEquals(1, result.nextState().deadlineGeneration());
        assertEquals(T0.plusMillis(bidAt), ((LaneEvent.BidAccepted)result.events().getFirst()).acceptedAt());
        assertTrue(result.effects().stream().noneMatch(LaneEffect.ScheduleTimer.class::isInstance));
    }

    @ParameterizedTest
    @CsvSource({"14999,true", "15000,false", "15001,false"})
    void bidBoundaryIsStrict(long at, boolean accepted) {
        var d = new LaneTestDriver(bidding(1), T0.plusMillis(at));
        var before = d.state();
        var result = d.send(firstBid());
        assertEquals(accepted, result.outcome() == LaneDecision.Outcome.APPLIED);
        if (!accepted) {
            assertEquals(BidRejection.LOT_NOT_BIDDING, result.bidRejection());
            assertSame(before, result.nextState());
            assertTrue(result.events().isEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({"14999,RESCHEDULED", "15000,APPLIED", "15001,APPLIED"})
    void closeBoundaryIsInclusive(long at, LaneDecision.Outcome expected) {
        var d = new LaneTestDriver(bidding(1), T0.plusMillis(at));
        var result = d.send(new LaneCommand.CheckDeadline(1, 1));
        assertEquals(expected, result.outcome());
        if (expected == LaneDecision.Outcome.APPLIED)
            assertEquals(T0.plusMillis(at + 5000), result.nextState().gapEndsAt());
    }

    @Test void approvedCustomDurationsAreUsedWithoutCrossFieldRestrictions() {
        var initial = ready(2);
        var timing = new LaneTiming(Duration.ofMillis(1001), Duration.ofMillis(60000),
                Duration.ofMillis(1999), Duration.ofMillis(1000));
        initial = new LaneState(initial.phase(), initial.plans(), 1, initial.lot(), null, 0, 0, timing, POLICY);
        var d = new LaneTestDriver(initial, T0);
        d.send(new LaneCommand.Activate(T0));
        assertEquals(T0.plusMillis(1001), d.state().lot().deadlineAt());
        d.clock.set(T0.plusMillis(1000));
        d.send(firstBid());
        assertEquals(T0.plusMillis(61000), d.state().lot().deadlineAt());
        d.advanceTo(T0.plusMillis(61000));
        assertEquals(T0.plusMillis(62999), d.state().gapEndsAt());
        d.advanceTo(T0.plusMillis(62999));
        assertEquals(T0.plusMillis(64000), d.state().lot().deadlineAt());
    }

    @Test void repeatedAlternatingBidsHaveNoArtificialMaximumLotLength() {
        var d = new LaneTestDriver(ready(1), T0);
        d.send(new LaneCommand.Activate(T0));
        for (int i = 0; i < 100; i++) {
            var at = d.state().lot().deadlineAt().minusMillis(1);
            d.advanceTo(at);
            var lot = d.state().lot();
            var result = d.send(new LaneCommand.PlaceBid(1, 201 + i % 2, lot.currentPrice(), POLICY.nextPrice(lot.currentPrice())));
            assertEquals(LaneDecision.Outcome.APPLIED, result.outcome());
            assertEquals(at.plusSeconds(10), d.state().lot().deadlineAt());
        }
        assertEquals(100, d.state().lot().bidCount());
        assertTrue(d.state().lot().deadlineAt().isAfter(T0.plusSeconds(900)));
    }
}
