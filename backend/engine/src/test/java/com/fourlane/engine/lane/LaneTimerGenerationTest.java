package com.fourlane.engine.lane;

import com.fourlane.engine.support.LaneTestDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.fourlane.engine.lane.LaneFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneTimerGenerationTest {
    @Test void eightSecondBidKeepsGenerationAndOriginalCheckReschedulesAt18Seconds() {
        var d = new LaneTestDriver(ready(1), T0);
        d.send(new LaneCommand.Activate(T0));
        d.advanceTo(T0.plusSeconds(8));
        d.send(firstBid());
        assertEquals(1, d.state().deadlineGeneration());
        assertEquals(T0.plusSeconds(15), d.scheduler.reservations().getFirst().at());
        d.advanceTo(T0.plusSeconds(15));
        assertEquals(LanePhase.BIDDING, d.state().phase());
        assertEquals(LaneDecision.Outcome.RESCHEDULED, d.decisions.getLast().outcome());
        assertTrue(d.decisions.getLast().events().isEmpty());
        assertEquals(T0.plusSeconds(18), d.scheduler.reservations().getFirst().at());
        d.advanceTo(T0.plusSeconds(18));
        assertEquals(LotState.Status.SOLD, d.state().lot().status());
    }

    @ParameterizedTest
    @CsvSource({"1,0", "1,2", "99,1"})
    void invalidDeadlineIdentityIsIgnoredEvenAfterActualDeadline(long lotId, long generation) {
        var d = new LaneTestDriver(bidding(1), T0.plusSeconds(30));
        var before = d.state();
        var result = d.send(new LaneCommand.CheckDeadline(lotId, generation));
        assertSame(before, result.nextState());
        assertEquals(LaneDecision.Outcome.IGNORED, result.outcome());
        assertTrue(result.effects().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"1,0", "1,2", "99,1"})
    void invalidGapIdentityCannotStartNextLotEvenAfterGap(long lotId, long generation) {
        var initial = LaneTransitionTableTest.inPhase(LanePhase.GAP);
        var result = new LaneStateMachine().handle(initial, new LaneCommand.EndGap(lotId, generation), T0.plusSeconds(30));
        assertSame(initial, result.nextState());
        assertTrue(result.effects().isEmpty());
    }

    @Test void oldAndDuplicateTimersDoNotChangeLaterLotsOrFinishTwice() {
        var d = new LaneTestDriver(ready(2), T0);
        d.send(new LaneCommand.Activate(T0));
        d.advanceTo(T0.plusSeconds(20));
        assertEquals(2, d.state().deadlineGeneration());
        var second = d.state();
        assertSame(second, d.send(new LaneCommand.EndGap(1, 1)).nextState());
        d.advanceTo(T0.plusSeconds(35));
        assertEquals(2, d.state().gapGeneration());
        d.clock.set(T0.plusSeconds(40));
        var gap = d.state();
        assertSame(gap, d.send(new LaneCommand.CheckDeadline(1, 1)).nextState());
        assertSame(gap, d.send(new LaneCommand.CheckDeadline(2, 2)).nextState());
        assertSame(gap, d.send(new LaneCommand.EndGap(1, 1)).nextState());
        d.send(new LaneCommand.EndGap(2, 2));
        var finished = d.state();
        assertSame(finished, d.send(new LaneCommand.EndGap(2, 2)).nextState());
        assertEquals(2, d.events().stream().filter(LaneEvent.LotClosed.class::isInstance).count());
        assertEquals(1, d.events().stream().filter(LaneEvent.LaneFinished.class::isInstance).count());
    }

    @Test void endGapBeforeBoundaryDoesNothingAndTickRecoversMissingTimer() {
        var gap = LaneTransitionTableTest.inPhase(LanePhase.GAP);
        var d = new LaneTestDriver(gap, T0.plusMillis(19999)); // deliberately no real or fake reservation
        assertSame(gap, d.send(new LaneCommand.EndGap(1, 1)).nextState());
        assertSame(gap, d.send(new LaneCommand.Tick()).nextState());
        assertTrue(d.scheduler.reservations().isEmpty());
        d.clock.set(T0.plusSeconds(20));
        assertEquals(2, d.send(new LaneCommand.Tick()).nextState().position());
        assertEquals(T0.plusSeconds(35), d.state().lot().deadlineAt());
    }

    @Test void tickClosesMissingDeadlineTimerAtObservedTimeAndPreservesFullResultGap() {
        var d = new LaneTestDriver(bidding(1), T0.plusSeconds(16));
        d.send(new LaneCommand.Tick());
        assertEquals(LanePhase.GAP, d.state().phase());
        assertEquals(T0.plusSeconds(16), d.state().lot().closedAt());
        assertEquals(T0.plusSeconds(21), d.state().gapEndsAt());
        var gap = d.state();
        assertSame(gap, d.send(new LaneCommand.Tick()).nextState());
        d.advanceTo(T0.plusSeconds(21));
        assertEquals(LanePhase.FINISHED, d.state().phase());
    }

    @Test void earlyTickDoesNotCreateExtraReservations() {
        var d = new LaneTestDriver(bidding(1), T0.plusSeconds(2));
        var before = d.state();
        var result = d.send(new LaneCommand.Tick());
        assertSame(before, result.nextState());
        assertTrue(result.effects().isEmpty());
    }
}
