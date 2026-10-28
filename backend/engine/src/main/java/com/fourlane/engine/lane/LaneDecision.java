package com.fourlane.engine.lane;
import java.time.Instant;
import java.util.List;
public record LaneDecision(LaneState nextState, Instant processedAt, Outcome outcome,
        BidRejection bidRejection, ActivationRejection activationRejection, List<LaneEffect> effects) {
    public enum Outcome { APPLIED, IGNORED, BID_REJECTED, ACTIVATION_REJECTED }
    public enum ActivationRejection { NOT_READY, EMPTY_LINEUP }
    public List<LaneEvent> events() {
        return effects.stream().filter(LaneEffect.PublishEvent.class::isInstance)
                .map(e -> ((LaneEffect.PublishEvent)e).event()).toList();
    }
}
