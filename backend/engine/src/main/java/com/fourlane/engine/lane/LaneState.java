package com.fourlane.engine.lane;

import com.fourlane.engine.policy.BidIncrementPolicy;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable business state. The supplied policy must be the immutable policy fixed for the session.
 * Processing-time metadata, generated bid IDs and publication sequences belong to the outer driver.
 * PAUSED_BIDDING retains its historical deadline internally; this is not a public snapshot.
 */
public record LaneState(LanePhase phase, List<LotPlan> plans, int position, LotState lot,
        Instant gapEndsAt, long deadlineGeneration, long gapGeneration,
        LaneTiming timing, BidIncrementPolicy bidPolicy) {
    public LaneState {
        Objects.requireNonNull(phase);
        Objects.requireNonNull(timing);
        Objects.requireNonNull(bidPolicy);
        plans = List.copyOf(plans);
        var ids = new HashSet<Long>();
        if (plans.stream().anyMatch(p -> !ids.add(p.lotId())))
            throw new IllegalArgumentException("Duplicate lot IDs");
        if (deadlineGeneration < 0 || gapGeneration < 0)
            throw new IllegalArgumentException("Negative timer generation");
        if (plans.isEmpty()) {
            if (phase != LanePhase.READY || position != 0 || lot != null || gapEndsAt != null)
                throw new IllegalArgumentException("Only READY can have an empty lineup");
        } else {
            if (position < 1 || position > plans.size() || lot == null)
                throw new IllegalArgumentException("Invalid current lot position");
            var plan = plans.get(position - 1);
            if (lot.currentPrice() < plan.startPrice()
                    || (lot.bidCount() == 0 && lot.currentPrice() != plan.startPrice())
                    || (lot.status() == LotState.Status.SOLD && lot.currentPrice() < plan.reservePrice())
                    || (lot.unsoldReason() == LotState.UnsoldReason.RESERVE_NOT_MET
                        && lot.currentPrice() >= plan.reservePrice()))
                throw new IllegalArgumentException("Price facts disagree with the plan");
            boolean valid = switch (phase) {
                case READY -> position == 1 && lot.status() == LotState.Status.PENDING;
                case BIDDING, PAUSED_BIDDING -> lot.status() == LotState.Status.BIDDING && deadlineGeneration > 0;
                case GAP, PAUSED_GAP -> lot.closed() && gapGeneration > 0;
                case FINISHED -> lot.closed() && position == plans.size();
            };
            if (!valid) throw new IllegalArgumentException("Lane and lot phases disagree");
            if (phase == LanePhase.GAP) {
                LaneProcessingTime.requireMillis(gapEndsAt);
                if (!gapEndsAt.isAfter(lot.closedAt()))
                    throw new IllegalArgumentException("Gap must follow closure");
            } else if (gapEndsAt != null) {
                throw new IllegalArgumentException("Only GAP has gapEndsAt");
            }
        }
    }

    public LotPlan currentPlan() {
        if (lot == null) throw new IllegalStateException("No current lot");
        return plans.get(position - 1);
    }
    public boolean hasNextLot() { return position < plans.size(); }

    LaneState transition(LanePhase nextPhase, int nextPosition, LotState nextLot, Instant nextGap,
            long nextDeadlineGeneration, long nextGapGeneration) {
        return new LaneState(nextPhase, plans, nextPosition, nextLot, nextGap,
                nextDeadlineGeneration, nextGapGeneration, timing, bidPolicy);
    }
}
