package com.fourlane.auction.policy.application;

import java.time.Instant;
import java.util.List;

public record BidPolicyView(long policyId, int version, List<Tier> tiers, Instant createdAt,
        String createdByNickname, boolean isLatest) {
    public record Tier(long minPrice, long increment) {}
    public BidPolicyView { tiers = List.copyOf(tiers); }
}
