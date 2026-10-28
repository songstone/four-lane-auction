package com.fourlane.engine.lane;

import com.fourlane.engine.support.LaneTestDriver;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.fourlane.engine.lane.LaneFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Concurrent submission plus a TEST-ONLY serial gate; not evidence of a production DB lock. */
class LaneConcurrentDispatchTest {
    @ParameterizedTest(name = "bidFirst={0}: 500 concurrent submissions")
    @ValueSource(booleans = {true, false})
    void bothForcedSerialOrdersPreserveTheAuctionResultFor500Rounds(boolean bidFirst) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 500; round++) {
                var driver = new LaneTestDriver(bidding(2), T0);
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                var firstDone = new CountDownLatch(1);
                Future<LaneDecision> first = executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start gate timed out");
                    try {
                        synchronized (driver) {
                            driver.clock.set(bidFirst ? T0.plusMillis(14999) : T0.plusSeconds(15));
                            return driver.send(bidFirst ? firstBid() : new LaneCommand.CheckDeadline(1, 1));
                        }
                    } finally { firstDone.countDown(); }
                });
                Future<LaneDecision> second = executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS) || !firstDone.await(5, TimeUnit.SECONDS))
                        throw new AssertionError("Serial order gate timed out");
                    synchronized (driver) {
                        driver.clock.set(T0.plusSeconds(15));
                        return driver.send(bidFirst ? new LaneCommand.CheckDeadline(1, 1) : firstBid());
                    }
                });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                assertEquals(LaneDecision.Outcome.APPLIED, first.get(5, TimeUnit.SECONDS).outcome(), "round " + round);
                var secondResult = second.get(5, TimeUnit.SECONDS);
                if (bidFirst) {
                    assertEquals(LaneDecision.Outcome.RESCHEDULED, secondResult.outcome());
                    driver.advanceTo(T0.plusMillis(24999));
                    assertEquals(LotState.Status.SOLD, driver.state().lot().status());
                    var accepted = driver.events().stream().filter(LaneEvent.BidAccepted.class::isInstance)
                            .map(LaneEvent.BidAccepted.class::cast).toList();
                    assertEquals(1, accepted.size());
                    assertEquals(accepted.getFirst().bidderId(), driver.state().lot().highestBidderId());
                    assertEquals(accepted.getFirst().amount(), driver.state().lot().currentPrice());
                } else {
                    assertEquals(BidRejection.LOT_NOT_BIDDING, secondResult.bidRejection());
                    assertEquals(LotState.UnsoldReason.NO_BID, driver.state().lot().unsoldReason());
                    assertTrue(driver.events().stream().noneMatch(LaneEvent.BidAccepted.class::isInstance));
                }
                assertEquals(1, driver.events().stream().filter(LaneEvent.LotClosed.class::isInstance).count());
            }
        }
    }
}
