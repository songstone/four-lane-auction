package com.fourlane.engine.lane;
/** PRD 6.3 conditions 5 through 9; HTTP mapping belongs to app. */
public enum BidRejection {
    LANE_NOT_RUNNING, LOT_NOT_BIDDING, LOT_MISMATCH, PRICE_CHANGED,
    INVALID_BID_AMOUNT, ALREADY_HIGHEST_BIDDER
}
