package com.fourlane.engine.policy;

/** Monetary values are won; the caller supplies the current price (start price before any bid). */
public interface BidIncrementPolicy {
    long incrementFor(long currentPrice);

    default long nextPrice(long currentPrice) {
        return Math.addExact(currentPrice, incrementFor(currentPrice));
    }
}
