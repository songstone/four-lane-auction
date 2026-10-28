package com.fourlane.auction.policy.application;

import com.fourlane.engine.policy.*;

/** App owns persistence-to-engine conversion; engine never sees an entity or a Spring type. */
public final class BidPolicyMapper {
    private BidPolicyMapper() {}
    public static BidIncrementPolicy toEngine(BidPolicyView policy) {
        return new TieredBidIncrementPolicy(policy.tiers().stream()
                .map(t -> new TieredBidIncrementPolicy.Tier(t.minPrice(), t.increment())).toList());
    }
}
