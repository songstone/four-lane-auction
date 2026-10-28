package com.fourlane.engine.lane;

import com.fourlane.engine.support.LaneTestDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.fourlane.engine.lane.LaneFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneLifecycleTest {
    @Test void noBidClosesAt15SecondsAndNextLotStartsAfterFullGap() {
        var d = new LaneTestDriver(ready(2), T0);
        var activation = d.send(new LaneCommand.Activate(T0));
        assertEquals(0, activation.effects().stream().filter(LaneEffect.PersistChange.class::isInstance).count());
        assertEquals(1, activation.events().size()); // first start was persisted by the session start transaction
        d.advanceTo(T0.plusMillis(14999));
        assertEquals(LanePhase.BIDDING, d.state().phase());
        d.advanceTo(T0.plusSeconds(15));
        assertEquals(LotState.Status.UNSOLD, d.state().lot().status());
        var result = (LaneEvent.LotClosed)d.events().getLast();
        assertEquals(LotState.UnsoldReason.NO_BID, result.reason());
        assertNull(result.finalPrice());
        assertNull(result.winnerId());
        assertEquals(T0.plusSeconds(20), d.state().gapEndsAt());
        d.advanceTo(T0.plusMillis(19999));
        assertEquals(1, d.state().position());
        d.advanceTo(T0.plusSeconds(20));
        assertEquals(2, d.state().position());
        assertEquals(LanePhase.BIDDING, d.state().phase());
        assertEquals(T0.plusSeconds(35), d.state().lot().deadlineAt());
        assertEquals(0, d.state().lot().bidCount());
        assertEquals(50000, d.state().lot().currentPrice());
    }

    @ParameterizedTest
    @CsvSource({"50000,false,UNSOLD,NO_BID", "60000,true,SOLD,", "61000,true,UNSOLD,RESERVE_NOT_MET"})
    void closureOutcomeAndFinalGap(long reserve, boolean bid, LotState.Status status, LotState.UnsoldReason reason) {
        var initial = ready(1);
        initial = new LaneState(initial.phase(), java.util.List.of(new LotPlan(1, 101, 50000, reserve)),
                1, initial.lot(), null, 0, 0, TIMING, POLICY);
        var d = new LaneTestDriver(initial, T0);
        d.send(new LaneCommand.Activate(T0));
        if (bid) {
            d.clock.set(T0.plusSeconds(2));
            d.send(firstBid());
        }
        d.advanceTo(T0.plusSeconds(15));
        assertEquals(status, d.state().lot().status());
        assertEquals(reason, d.state().lot().unsoldReason());
        var close = d.decisions.getLast();
        var fact = (LaneEvent.LotClosed)close.events().getFirst();
        assertEquals(bid ? Long.valueOf(60000) : null, fact.finalPrice());
        assertEquals(status == LotState.Status.SOLD ? Long.valueOf(201) : null, fact.winnerId());
        assertInstanceOf(LaneEffect.PersistChange.class, close.effects().getFirst());
        d.advanceTo(T0.plusMillis(19999));
        assertEquals(LanePhase.GAP, d.state().phase());
        d.advanceTo(T0.plusSeconds(20));
        assertEquals(LanePhase.FINISHED, d.state().phase());
        assertEquals(1, d.events().stream().filter(LaneEvent.LaneFinished.class::isInstance).count());
        assertTrue(d.scheduler.reservations().isEmpty());
        var finished = d.state();
        d.clock.set(T0.plusSeconds(100));
        for (var command : java.util.List.of(new LaneCommand.Tick(), new LaneCommand.EndGap(1, 1),
                new LaneCommand.CheckDeadline(1, 1))) {
            var ignored = d.send(command);
            assertSame(finished, ignored.nextState());
            assertTrue(ignored.effects().isEmpty());
        }
    }

    @Test void delayedActivationUsesCommonT0AndDelayedEndGapUsesActualProcessingTime() {
        var d = new LaneTestDriver(ready(2), T0.plusSeconds(3));
        d.send(new LaneCommand.Activate(T0));
        assertEquals(T0, d.state().lot().startedAt());
        assertEquals(T0.plusSeconds(15), d.state().lot().deadlineAt());
        d.clock.set(T0.plusMillis(15400));
        d.scheduler.runDue(d::send);
        assertEquals(T0.plusMillis(20400), d.state().gapEndsAt());
        d.clock.set(T0.plusSeconds(21));
        d.scheduler.runDue(d::send);
        assertEquals(T0.plusSeconds(21), d.state().lot().startedAt());
        assertEquals(T0.plusSeconds(36), d.state().lot().deadlineAt());
    }

    @Test void emptyLineupAndDuplicateActivationProduceOnlyInternalRejections() {
        var empty = new LaneTestDriver(ready(0), T0);
        assertEquals(LaneDecision.ActivationRejection.EMPTY_LINEUP,
                empty.send(new LaneCommand.Activate(T0)).activationRejection());
        var d = new LaneTestDriver(ready(1), T0);
        d.send(new LaneCommand.Activate(T0));
        var state = d.state();
        var again = d.send(new LaneCommand.Activate(T0));
        assertSame(state, again.nextState());
        assertEquals(LaneDecision.ActivationRejection.NOT_READY, again.activationRejection());
        assertTrue(again.effects().isEmpty());
    }

    @Test void oneLaneCompletingDoesNotChangeAnotherLane() {
        var left = new LaneTestDriver(ready(1), T0);
        var right = new LaneTestDriver(ready(2), T0);
        left.send(new LaneCommand.Activate(T0));
        right.send(new LaneCommand.Activate(T0));
        var rightState = right.state();
        left.advanceTo(T0.plusSeconds(20));
        assertEquals(LanePhase.FINISHED, left.state().phase());
        assertSame(rightState, right.state());
        assertEquals(T0, right.clock.instant());
    }
}
