package com.fourlane.engine.lane;
import java.time.Instant;
/** Internal facts, not wire messages: app supplies generated IDs, commit times, epoch and seq. */
public sealed interface LaneEvent {
    record LotStarted(long lotId, Instant startedAt, Instant deadlineAt) implements LaneEvent {}
    record BidAccepted(long lotId, long bidderId, long amount, Instant acceptedAt, Instant deadlineAt) implements LaneEvent {}
    record LotClosed(long lotId, LotState.Status outcome, LotState.UnsoldReason reason,
            Long finalPrice, Long winnerId, Instant closedAt) implements LaneEvent {}
    record LaneFinished(Instant finishedAt) implements LaneEvent {}
}
