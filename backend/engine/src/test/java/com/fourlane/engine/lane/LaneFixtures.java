package com.fourlane.engine.lane;

import com.fourlane.engine.policy.TieredBidIncrementPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

final class LaneFixtures {
    static final Instant T0 = Instant.parse("2026-10-28T00:00:00.000Z");
    static final LaneTiming TIMING = new LaneTiming(Duration.ofSeconds(15), Duration.ofSeconds(10),
            Duration.ofSeconds(5), Duration.ofSeconds(10));
    static final TieredBidIncrementPolicy POLICY = new TieredBidIncrementPolicy(List.of(
            new TieredBidIncrementPolicy.Tier(0, 1000),
            new TieredBidIncrementPolicy.Tier(10000, 10000),
            new TieredBidIncrementPolicy.Tier(100000, 25000)));
    static LotPlan plan(long id) { return new LotPlan(id, 100 + id, 50000, 50000); }
    static LaneState ready(int count) {
        var plans = java.util.stream.LongStream.rangeClosed(1, count).mapToObj(LaneFixtures::plan).toList();
        return new LaneState(LanePhase.READY, plans, count == 0 ? 0 : 1,
                count == 0 ? null : new LotState(LotState.Status.PENDING, 50000, null, 0, null, null, null, null),
                null, 0, 0, TIMING, POLICY);
    }
    static LaneState bidding(int count) {
        var ready = ready(count);
        return new LaneState(LanePhase.BIDDING, ready.plans(), 1,
                new LotState(LotState.Status.BIDDING, 50000, null, 0, T0, T0.plusSeconds(15), null, null),
                null, 1, 0, TIMING, POLICY);
    }
    static LaneCommand.PlaceBid firstBid() { return new LaneCommand.PlaceBid(1, 201, 50000, 60000); }
}
