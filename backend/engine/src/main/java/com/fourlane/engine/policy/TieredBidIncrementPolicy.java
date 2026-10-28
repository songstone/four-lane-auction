package com.fourlane.engine.policy;

import java.util.List;
import java.util.Objects;

/** Immutable Strategy. Each lower bound is inclusive; supplied order must already be increasing. */
public final class TieredBidIncrementPolicy implements BidIncrementPolicy {
    public record Tier(long minPrice, long increment) {}
    private final List<Tier> tiers;

    public TieredBidIncrementPolicy(List<Tier> tiers) {
        this.tiers = List.copyOf(Objects.requireNonNull(tiers, "tiers"));
        if (this.tiers.isEmpty() || this.tiers.getFirst().minPrice() != 0)
            throw new IllegalArgumentException("First tier must start at zero");
        long previous = -1;
        for (var tier : this.tiers) {
            if (tier.minPrice() <= previous || tier.increment() <= 0 || tier.increment() % 1000 != 0)
                throw new IllegalArgumentException("Invalid or unordered tier");
            previous = tier.minPrice();
        }
    }

    @Override public long incrementFor(long currentPrice) {
        if (currentPrice < 0) throw new IllegalArgumentException("Negative current price");
        int low = 0, high = tiers.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (tiers.get(middle).minPrice() <= currentPrice) low = middle + 1;
            else high = middle;
        }
        return tiers.get(low - 1).increment();
    }
}
