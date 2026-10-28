package com.fourlane.engine.lane;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static com.fourlane.engine.lane.LaneFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneTransitionTableTest {
    // Explicit expected outcomes from ADR-014. Never derive these from production dispatch.
    private static final String CASES = """
        READY,Activate,APPLIED,BIDDING,LotStarted,-
        READY,PlaceBid,BID_REJECTED,READY,-,LANE_NOT_RUNNING
        READY,CheckDeadline,IGNORED,READY,-,-
        READY,EndGap,IGNORED,READY,-,-
        READY,Tick,IGNORED,READY,-,-
        BIDDING,Activate,ACTIVATION_REJECTED,BIDDING,-,-
        BIDDING,PlaceBid,APPLIED,BIDDING,BidAccepted,-
        BIDDING,CheckDeadline,APPLIED,GAP,LotClosed,-
        BIDDING,EndGap,IGNORED,BIDDING,-,-
        BIDDING,Tick,APPLIED,GAP,LotClosed,-
        GAP,Activate,ACTIVATION_REJECTED,GAP,-,-
        GAP,PlaceBid,BID_REJECTED,GAP,-,LOT_NOT_BIDDING
        GAP,CheckDeadline,IGNORED,GAP,-,-
        GAP,EndGap,APPLIED,BIDDING,LotStarted,-
        GAP,Tick,APPLIED,BIDDING,LotStarted,-
        PAUSED_BIDDING,Activate,ACTIVATION_REJECTED,PAUSED_BIDDING,-,-
        PAUSED_BIDDING,PlaceBid,BID_REJECTED,PAUSED_BIDDING,-,LANE_NOT_RUNNING
        PAUSED_BIDDING,CheckDeadline,IGNORED,PAUSED_BIDDING,-,-
        PAUSED_BIDDING,EndGap,IGNORED,PAUSED_BIDDING,-,-
        PAUSED_BIDDING,Tick,IGNORED,PAUSED_BIDDING,-,-
        PAUSED_GAP,Activate,ACTIVATION_REJECTED,PAUSED_GAP,-,-
        PAUSED_GAP,PlaceBid,BID_REJECTED,PAUSED_GAP,-,LANE_NOT_RUNNING
        PAUSED_GAP,CheckDeadline,IGNORED,PAUSED_GAP,-,-
        PAUSED_GAP,EndGap,IGNORED,PAUSED_GAP,-,-
        PAUSED_GAP,Tick,IGNORED,PAUSED_GAP,-,-
        FINISHED,Activate,ACTIVATION_REJECTED,FINISHED,-,-
        FINISHED,PlaceBid,BID_REJECTED,FINISHED,-,LANE_NOT_RUNNING
        FINISHED,CheckDeadline,IGNORED,FINISHED,-,-
        FINISHED,EndGap,IGNORED,FINISHED,-,-
        FINISHED,Tick,IGNORED,FINISHED,-,-
        """;

    static Stream<Arguments> cases() { return CASES.lines().map(row -> Arguments.of((Object[])row.split(","))); }

    static LaneState inPhase(LanePhase phase) {
        if (phase == LanePhase.READY) return ready(2);
        var source = bidding(phase == LanePhase.FINISHED ? 1 : 2);
        if (phase == LanePhase.BIDDING) return source;
        if (phase == LanePhase.PAUSED_BIDDING)
            return new LaneState(phase, source.plans(), 1, source.lot(), null, 1, 0, TIMING, POLICY);
        var lot = new LotState(LotState.Status.UNSOLD, 50000, null, 0, T0, null,
                T0.plusSeconds(15), LotState.UnsoldReason.NO_BID);
        return new LaneState(phase, source.plans(), 1, lot,
                phase == LanePhase.GAP ? T0.plusSeconds(20) : null, 1, 1, TIMING, POLICY);
    }

    @ParameterizedTest(name = "{0} x {1} -> {2} / {3}")
    @MethodSource("cases")
    void everyPhaseAndCommandHasAnExplicitExpectation(String phaseName, String commandName,
            String outcomeName, String nextPhaseName, String eventName, String rejectionName) {
        var phase = LanePhase.valueOf(phaseName);
        var state = inPhase(phase);
        LaneCommand command = switch (commandName) {
            case "Activate" -> new LaneCommand.Activate(T0);
            case "PlaceBid" -> firstBid();
            case "CheckDeadline" -> new LaneCommand.CheckDeadline(1, 1);
            case "EndGap" -> new LaneCommand.EndGap(1, 1);
            case "Tick" -> new LaneCommand.Tick();
            default -> throw new AssertionError(commandName);
        };
        var at = command instanceof LaneCommand.PlaceBid ? T0.plusSeconds(2)
                : phase == LanePhase.BIDDING ? T0.plusSeconds(15) : T0.plusSeconds(20);
        var result = new LaneStateMachine().handle(state, command, at);
        assertEquals(LaneDecision.Outcome.valueOf(outcomeName), result.outcome());
        assertEquals(LanePhase.valueOf(nextPhaseName), result.nextState().phase());
        assertEquals(rejectionName.equals("-") ? null : BidRejection.valueOf(rejectionName), result.bidRejection());
        if (result.outcome() == LaneDecision.Outcome.ACTIVATION_REJECTED)
            assertEquals(LaneDecision.ActivationRejection.NOT_READY, result.activationRejection());
        if (eventName.equals("-")) {
            assertTrue(result.effects().isEmpty());
            assertSame(state, result.nextState());
        } else {
            assertEquals(1, result.events().size());
            assertEquals(eventName, result.events().getFirst().getClass().getSimpleName());
        }
    }

    @Test void tableHasExactlyOneCellForEverySealedCommandAndPhase() {
        var actual = new HashSet<String>();
        CASES.lines().forEach(row -> {
            var fields = row.split(",");
            assertTrue(actual.add(fields[0] + ":" + fields[1]), "Duplicate cell");
        });
        var expected = new HashSet<String>();
        for (var phase : LanePhase.values())
            for (var command : LaneCommand.class.getPermittedSubclasses())
                expected.add(phase.name() + ":" + command.getSimpleName());
        assertEquals(30, actual.size());
        assertEquals(expected, actual);
    }
}
