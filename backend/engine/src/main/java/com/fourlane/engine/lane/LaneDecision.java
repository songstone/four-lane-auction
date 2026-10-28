package com.fourlane.engine.lane;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The driver may advance processedAt on a no-op without changing any business state. */
public record LaneDecision(LaneState nextState, Instant processedAt, Outcome outcome,
        BidRejection bidRejection, ActivationRejection activationRejection, List<LaneEffect> effects) {
    public enum Outcome { APPLIED, RESCHEDULED, IGNORED, BID_REJECTED, ACTIVATION_REJECTED }
    public enum ActivationRejection { NOT_READY, EMPTY_LINEUP }

    public LaneDecision {
        Objects.requireNonNull(nextState);
        Objects.requireNonNull(outcome);
        LaneProcessingTime.requireMillis(processedAt);
        effects = List.copyOf(effects);
        if ((outcome == Outcome.BID_REJECTED) != (bidRejection != null)
                || (outcome == Outcome.ACTIVATION_REJECTED) != (activationRejection != null))
            throw new IllegalArgumentException("Decision outcome and reason disagree");
        if ((outcome == Outcome.IGNORED || outcome == Outcome.BID_REJECTED
                || outcome == Outcome.ACTIVATION_REJECTED) && !effects.isEmpty())
            throw new IllegalArgumentException("Rejected or ignored commands cannot have effects");
    }

    public List<LaneEvent> events() {
        return effects.stream().filter(LaneEffect.PublishEvent.class::isInstance)
                .map(e -> ((LaneEffect.PublishEvent)e).event()).toList();
    }

    static LaneDecision ignored(LaneState state, Instant now) {
        return new LaneDecision(state, now, Outcome.IGNORED, null, null, List.of());
    }
    static LaneDecision rejectBid(LaneState state, Instant now, BidRejection reason) {
        return new LaneDecision(state, now, Outcome.BID_REJECTED, reason, null, List.of());
    }
    static LaneDecision rejectActivation(LaneState state, Instant now, ActivationRejection reason) {
        return new LaneDecision(state, now, Outcome.ACTIVATION_REJECTED, null, reason, List.of());
    }
}
