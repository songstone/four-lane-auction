package com.fourlane.engine.lane;

/** Internal pricing facts, never a public DTO. Reserve defaults are resolved before entering the engine. */
public record LotPlan(long lotId, long sellerId, long startPrice, long reservePrice) {
    public LotPlan {
        if (lotId <= 0 || sellerId <= 0 || startPrice <= 0 || reservePrice < startPrice)
            throw new IllegalArgumentException("Invalid lot plan");
    }
}
