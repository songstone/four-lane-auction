package com.fourlane.engine.lane;

import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.fourlane.engine.lane.LaneFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneStateValidationTest {
    @Test void stateAndDecisionCannotBeChangedThroughCollectionAliases() {
        var original = ready(2);
        var plans = new ArrayList<>(original.plans());
        var state = new LaneState(original.phase(), plans, 1, original.lot(), null, 0, 0, TIMING, POLICY);
        plans.clear();
        assertEquals(2, state.plans().size());
        assertThrows(UnsupportedOperationException.class, () -> state.plans().clear());
        var decision = new LaneStateMachine().handle(state, new LaneCommand.Activate(T0), T0);
        assertThrows(UnsupportedOperationException.class, () -> decision.effects().clear());
        assertThrows(UnsupportedOperationException.class, () -> decision.events().clear());
        assertEquals(LotState.Status.PENDING, state.lot().status());
    }

    @Test void cannotBuildFinishedLaneBeforeLastPositionOrWithOpenLot() {
        var bidding = bidding(2);
        assertThrows(IllegalArgumentException.class, () -> new LaneState(LanePhase.FINISHED, bidding.plans(),
                1, bidding.lot(), null, 1, 0, TIMING, POLICY));
        var gap = LaneTransitionTableTest.inPhase(LanePhase.GAP);
        assertThrows(IllegalArgumentException.class, () -> new LaneState(LanePhase.FINISHED, gap.plans(),
                1, gap.lot(), null, 1, 1, TIMING, POLICY));
        assertThrows(IllegalArgumentException.class, () -> new LaneState(LanePhase.GAP, gap.plans(),
                1, gap.lot(), T0.plusSeconds(15), 1, 1, TIMING, POLICY));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 999, 60001, Long.MAX_VALUE})
    void durationOutsideApprovedBoundsIsNotSilentlyAccepted(long milliseconds) {
        assertThrows(IllegalArgumentException.class, () -> new LaneTiming(Duration.ofMillis(milliseconds),
                Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }

    @Test void arithmeticOverflowFailsBeforeAnyDecisionOrStateMutation() {
        var base = bidding(1);
        var lot = new LotState(LotState.Status.BIDDING, Long.MAX_VALUE - 100, 201L, 1,
                T0, T0.plusSeconds(15), null, null);
        var state = new LaneState(base.phase(), base.plans(), 1, lot, null, 1, 0, TIMING, POLICY);
        assertThrows(ArithmeticException.class, () -> new LaneStateMachine().handle(state,
                new LaneCommand.PlaceBid(1, 202, Long.MAX_VALUE - 100, Long.MAX_VALUE), T0.plusSeconds(1)));
        assertEquals(Long.MAX_VALUE - 100, state.lot().currentPrice());
        // This detects overflow only. User-facing price-limit policy is M2-06, as in ADR-010.
    }
}
