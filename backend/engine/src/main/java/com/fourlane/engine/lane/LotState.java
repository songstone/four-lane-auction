package com.fourlane.engine.lane;

import java.time.Instant;
import java.util.Objects;

/** Current lot facts. Historical results are persisted by the driver, not kept as an engine event log. */
public record LotState(Status status, long currentPrice, Long highestBidderId, int bidCount,
        Instant startedAt, Instant deadlineAt, Instant closedAt, UnsoldReason unsoldReason) {
    public enum Status { PENDING, BIDDING, SOLD, UNSOLD }
    public enum UnsoldReason { NO_BID, RESERVE_NOT_MET }

    public LotState {
        Objects.requireNonNull(status);
        if (currentPrice < 0 || bidCount < 0 || (bidCount == 0) != (highestBidderId == null)
                || (highestBidderId != null && highestBidderId <= 0))
            throw new IllegalArgumentException("Inconsistent bid facts");
        if (startedAt != null) LaneProcessingTime.requireMillis(startedAt);
        if (deadlineAt != null) LaneProcessingTime.requireMillis(deadlineAt);
        if (closedAt != null) LaneProcessingTime.requireMillis(closedAt);
        boolean valid = switch (status) {
            case PENDING -> bidCount == 0 && startedAt == null && deadlineAt == null
                    && closedAt == null && unsoldReason == null;
            case BIDDING -> startedAt != null && deadlineAt != null && deadlineAt.isAfter(startedAt)
                    && closedAt == null && unsoldReason == null;
            case SOLD -> bidCount > 0 && startedAt != null && deadlineAt == null
                    && closedAt != null && !closedAt.isBefore(startedAt) && unsoldReason == null;
            case UNSOLD -> startedAt != null && deadlineAt == null && closedAt != null
                    && !closedAt.isBefore(startedAt)
                    && (bidCount == 0 ? unsoldReason == UnsoldReason.NO_BID
                                    : unsoldReason == UnsoldReason.RESERVE_NOT_MET);
        };
        if (!valid) throw new IllegalArgumentException("Inconsistent lot phase");
    }

    public boolean closed() { return status == Status.SOLD || status == Status.UNSOLD; }
}
