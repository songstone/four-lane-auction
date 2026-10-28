package com.fourlane.engine.lane;
import java.time.Instant;
public record LotState(Status status, long currentPrice, Long highestBidderId, int bidCount,
        Instant startedAt, Instant deadlineAt, Instant closedAt, UnsoldReason unsoldReason) {
    public enum Status { PENDING, BIDDING, SOLD, UNSOLD }
    public enum UnsoldReason { NO_BID, RESERVE_NOT_MET }
}
