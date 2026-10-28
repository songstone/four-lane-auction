package com.fourlane.engine.lane;

import com.fourlane.engine.support.LaneTestDriver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static com.fourlane.engine.lane.LaneFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneBidValidationTest {
    @ParameterizedTest(name = "{0}: first failure")
    @CsvSource({
        "LOT_MISMATCH,99,1,1,201",
        "PRICE_CHANGED,1,1,1,201",
        "INVALID_BID_AMOUNT,1,60000,1,201",
        "ALREADY_HIGHEST_BIDDER,1,60000,70000,201"
    })
    void firstFailureWinsAmongConditionsSixThroughNine(BidRejection expected, long lotId, long price, long amount, long bidder) {
        var d = new LaneTestDriver(bidding(2), T0.plusSeconds(2));
        d.send(firstBid());
        var before = d.state();
        var result = d.send(new LaneCommand.PlaceBid(lotId, bidder, price, amount));
        assertEquals(expected, result.bidRejection());
        assertSame(before, result.nextState());
        assertTrue(result.effects().isEmpty());
    }

    @Test void conditionFiveWinsEvenWhenLotPriceAmountAndBidderAreWrong() {
        var command = new LaneCommand.PlaceBid(99, 201, 1, 1);
        var ready = new LaneTestDriver(ready(2), T0);
        assertEquals(BidRejection.LANE_NOT_RUNNING, ready.send(command).bidRejection());
        var d = new LaneTestDriver(bidding(2), T0.plusSeconds(2));
        d.send(firstBid());
        d.clock.set(T0.plusSeconds(15));
        var before = d.state();
        assertEquals(BidRejection.LOT_NOT_BIDDING, d.send(command).bidRejection());
        assertSame(before, d.state());
        d.send(new LaneCommand.CheckDeadline(1, 1));
        assertEquals(BidRejection.LOT_NOT_BIDDING, d.send(command).bidRejection());
    }

    @Test void firstBidMustBeStartPricePlusIncrement() {
        var d = new LaneTestDriver(bidding(1), T0);
        assertEquals(BidRejection.INVALID_BID_AMOUNT,
                d.send(new LaneCommand.PlaceBid(1, 201, 50000, 50000)).bidRejection());
        var result = d.send(firstBid());
        assertEquals(60000, result.nextState().lot().currentPrice());
        assertEquals(1, result.nextState().lot().bidCount());
    }

    @Test void oldLotBidCannotLandOnNextLotWithSameStartPrice() {
        var d = new LaneTestDriver(ready(2), T0);
        d.send(new LaneCommand.Activate(T0));
        d.advanceTo(T0.plusSeconds(20));
        var nextLot = d.state();
        assertEquals(2, nextLot.currentPlan().lotId());
        var result = d.send(firstBid());
        assertEquals(BidRejection.LOT_MISMATCH, result.bidRejection());
        assertSame(nextLot, result.nextState());
        assertTrue(result.effects().isEmpty());
    }

    @Test void nextBidUsesTheSuppliedFixedPolicyAndLeavesTheInputSnapshotUnchanged() {
        var d = new LaneTestDriver(bidding(1), T0);
        var input = d.state();
        var first = d.send(firstBid());
        var second = d.send(new LaneCommand.PlaceBid(1, 202, 60000, 70000));
        assertEquals(LaneDecision.Outcome.APPLIED, second.outcome());
        assertEquals(201L, first.nextState().lot().highestBidderId());
        assertEquals(202L, second.nextState().lot().highestBidderId());
        assertEquals(50000, input.lot().currentPrice());
        assertEquals(0, input.lot().bidCount());
        assertSame(POLICY, second.nextState().bidPolicy());
    }
}
