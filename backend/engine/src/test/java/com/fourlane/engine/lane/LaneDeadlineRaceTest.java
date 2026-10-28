package com.fourlane.engine.lane;

import com.fourlane.engine.support.LaneTestDriver;
import org.junit.jupiter.api.Test;
import static com.fourlane.engine.lane.LaneFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Written and executed RED before the judgment implementation (PRD 9.7, AC-10-4/5). */
class LaneDeadlineRaceTest {
    @Test void bidBeforeDeadlineWinsThenOldCheckUsesExtendedDeadline() {
        var driver = new LaneTestDriver(bidding(2), T0.plusMillis(14999));
        var bid = driver.send(firstBid());
        assertEquals(LaneDecision.Outcome.APPLIED, bid.outcome());
        assertEquals(T0.plusMillis(24999), driver.state().lot().deadlineAt());

        driver.clock.set(T0.plusSeconds(15));
        driver.send(new LaneCommand.CheckDeadline(1, 1));
        assertEquals(LanePhase.BIDDING, driver.state().phase());
        assertEquals(1, driver.state().lot().bidCount());
        assertTrue(driver.scheduler.reservations().stream().anyMatch(r -> r.at().equals(T0.plusMillis(24999))));

        driver.advanceTo(T0.plusMillis(24999));
        assertEquals(LotState.Status.SOLD, driver.state().lot().status());
        assertEquals(201L, driver.state().lot().highestBidderId());
        assertEquals(60000, driver.state().lot().currentPrice());
        assertEquals(1, driver.events().stream().filter(LaneEvent.LotClosed.class::isInstance).count());
    }

    @Test void deadlineCheckFirstClosesAndFollowingBidCannotChangeResult() {
        var driver = new LaneTestDriver(bidding(2), T0.plusSeconds(15));
        var close = driver.send(new LaneCommand.CheckDeadline(1, 1));
        assertEquals(LanePhase.GAP, close.nextState().phase());
        assertEquals(LotState.UnsoldReason.NO_BID, close.nextState().lot().unsoldReason());
        var closedState = driver.state();
        var bid = driver.send(firstBid());
        assertEquals(BidRejection.LOT_NOT_BIDDING, bid.bidRejection());
        assertSame(closedState, bid.nextState());
        assertTrue(bid.effects().isEmpty());
        assertEquals(1, driver.events().stream().filter(LaneEvent.LotClosed.class::isInstance).count());
    }

    @Test void bidAtDeadlinePlus200msIsRejectedBeforeCheckDelayed400ms() {
        var driver = new LaneTestDriver(bidding(2), T0);
        driver.scheduler.schedule(T0.plusSeconds(15), new LaneCommand.CheckDeadline(1, 1));
        driver.clock.set(T0.plusMillis(15200)); // clock moves without dispatching overdue timer
        var before = driver.state();
        var bid = driver.send(firstBid());
        assertEquals(BidRejection.LOT_NOT_BIDDING, bid.bidRejection());
        assertSame(before, bid.nextState());
        assertTrue(bid.effects().isEmpty());
        assertEquals(LanePhase.BIDDING, driver.state().phase());

        driver.clock.set(T0.plusMillis(15400));
        driver.scheduler.runDue(driver::send);
        assertEquals(LotState.Status.UNSOLD, driver.state().lot().status());
        assertEquals(T0.plusMillis(15400), driver.state().lot().closedAt());
        assertEquals(T0.plusMillis(20400), driver.state().gapEndsAt());
        assertTrue(driver.events().stream().noneMatch(LaneEvent.BidAccepted.class::isInstance));
    }
}
