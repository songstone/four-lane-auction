package com.fourlane.engine.lane;
/** Internal pricing facts, never a public DTO. */
public record LotPlan(long lotId, long sellerId, long startPrice, long reservePrice) {}
