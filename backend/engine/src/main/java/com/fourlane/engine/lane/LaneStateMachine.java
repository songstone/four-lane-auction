package com.fourlane.engine.lane;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure judgment. Callers serialize ALL lane commands, sample Clock once through LaneProcessingTime,
 * and adopt nextState / publish / schedule only after successful persistence.
 */
public final class LaneStateMachine {
    public LaneDecision handle(LaneState state, LaneCommand command, Instant processedAt) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(command);
        LaneProcessingTime.requireMillis(processedAt);
        return switch (command) {
            case LaneCommand.Activate activate -> activate(state, activate, processedAt);
            case LaneCommand.PlaceBid bid -> placeBid(state, bid, processedAt);
            case LaneCommand.CheckDeadline check -> checkDeadline(state, check, processedAt);
            case LaneCommand.EndGap end -> endGap(state, end, processedAt);
            case LaneCommand.Tick ignored -> tick(state, processedAt);
        };
    }

    private LaneDecision activate(LaneState state, LaneCommand.Activate command, Instant now) {
        if (state.phase() != LanePhase.READY)
            return LaneDecision.rejectActivation(state, now, LaneDecision.ActivationRejection.NOT_READY);
        if (state.plans().isEmpty())
            return LaneDecision.rejectActivation(state, now, LaneDecision.ActivationRejection.EMPTY_LINEUP);
        if (command.startedAt().isAfter(now))
            throw new IllegalArgumentException("Activation cannot precede committed session start");
        // t0 is common to all lanes, even when delivery of Activate is delayed.
        return startLot(state, 1, command.startedAt(), now, false);
    }

    private LaneDecision startLot(LaneState state, int position, Instant startedAt, Instant now, boolean persist) {
        var plan = state.plans().get(position - 1);
        var deadline = startedAt.plus(state.timing().firstBidWait());
        var lot = new LotState(LotState.Status.BIDDING, plan.startPrice(), null, 0,
                startedAt, deadline, null, null);
        var generation = Math.incrementExact(state.deadlineGeneration());
        var next = state.transition(LanePhase.BIDDING, position, lot, null, generation, state.gapGeneration());
        var event = new LaneEvent.LotStarted(plan.lotId(), startedAt, deadline);
        return applied(next, now, event, persist,
                new LaneEffect.ScheduleTimer(deadline, new LaneCommand.CheckDeadline(plan.lotId(), generation)));
    }

    private LaneDecision placeBid(LaneState state, LaneCommand.PlaceBid bid, Instant now) {
        // Validation order is contractual: do not move amount/bidder checks above lot/price checks.
        if (state.phase() != LanePhase.BIDDING && state.phase() != LanePhase.GAP)
            return LaneDecision.rejectBid(state, now, BidRejection.LANE_NOT_RUNNING);
        if (state.phase() == LanePhase.GAP || !now.isBefore(state.lot().deadlineAt()))
            return LaneDecision.rejectBid(state, now, BidRejection.LOT_NOT_BIDDING);
        if (bid.lotId() != state.currentPlan().lotId())
            return LaneDecision.rejectBid(state, now, BidRejection.LOT_MISMATCH);
        if (bid.expectedPrice() != state.lot().currentPrice())
            return LaneDecision.rejectBid(state, now, BidRejection.PRICE_CHANGED);
        if (bid.amount() != state.bidPolicy().nextPrice(state.lot().currentPrice()))
            return LaneDecision.rejectBid(state, now, BidRejection.INVALID_BID_AMOUNT);
        if (Objects.equals(bid.bidderId(), state.lot().highestBidderId()))
            return LaneDecision.rejectBid(state, now, BidRejection.ALREADY_HIGHEST_BIDDER);

        var proposedDeadline = now.plus(state.timing().softClose());
        var deadline = proposedDeadline.isAfter(state.lot().deadlineAt()) ? proposedDeadline : state.lot().deadlineAt();
        var lot = new LotState(LotState.Status.BIDDING, bid.amount(), bid.bidderId(),
                Math.incrementExact(state.lot().bidCount()), state.lot().startedAt(), deadline, null, null);
        var next = state.transition(LanePhase.BIDDING, state.position(), lot, null,
                state.deadlineGeneration(), state.gapGeneration());
        var event = new LaneEvent.BidAccepted(bid.lotId(), bid.bidderId(), bid.amount(), now, deadline);
        // Candidate B keeps the existing timer and its generation.
        return applied(next, now, event, true);
    }

    private LaneDecision checkDeadline(LaneState state, LaneCommand.CheckDeadline command, Instant now) {
        // Guard before ANY time-driven transition: a stale command must not close another lot.
        if (state.phase() != LanePhase.BIDDING || command.lotId() != state.currentPlan().lotId()
                || command.generation() != state.deadlineGeneration())
            return LaneDecision.ignored(state, now);
        if (now.isBefore(state.lot().deadlineAt()))
            return new LaneDecision(state, now, LaneDecision.Outcome.RESCHEDULED, null, null,
                    List.of(new LaneEffect.ScheduleTimer(state.lot().deadlineAt(), command)));
        return closeLot(state, now);
    }

    private LaneDecision closeLot(LaneState state, Instant now) {
        var previous = state.lot();
        var outcome = previous.bidCount() > 0 && previous.currentPrice() >= state.currentPlan().reservePrice()
                ? LotState.Status.SOLD : LotState.Status.UNSOLD;
        var reason = outcome == LotState.Status.SOLD ? null
                : previous.bidCount() == 0 ? LotState.UnsoldReason.NO_BID : LotState.UnsoldReason.RESERVE_NOT_MET;
        var lot = new LotState(outcome, previous.currentPrice(), previous.highestBidderId(),
                previous.bidCount(), previous.startedAt(), null, now, reason);
        var gapEndsAt = now.plus(state.timing().lotGap()); // actual closure, not the old deadline
        var generation = Math.incrementExact(state.gapGeneration());
        var next = state.transition(LanePhase.GAP, state.position(), lot, gapEndsAt,
                state.deadlineGeneration(), generation);
        var event = new LaneEvent.LotClosed(state.currentPlan().lotId(), outcome, reason,
                previous.bidCount() == 0 ? null : previous.currentPrice(),
                outcome == LotState.Status.SOLD ? previous.highestBidderId() : null, now);
        return applied(next, now, event, true,
                new LaneEffect.ScheduleTimer(gapEndsAt, new LaneCommand.EndGap(event.lotId(), generation)));
    }

    private LaneDecision endGap(LaneState state, LaneCommand.EndGap command, Instant now) {
        if (state.phase() != LanePhase.GAP || command.lotId() != state.currentPlan().lotId()
                || command.generation() != state.gapGeneration() || now.isBefore(state.gapEndsAt()))
            return LaneDecision.ignored(state, now);
        return advanceAfterGap(state, now);
    }

    private LaneDecision advanceAfterGap(LaneState state, Instant now) {
        if (state.hasNextLot()) return startLot(state, state.position() + 1, now, now, true);
        var next = state.transition(LanePhase.FINISHED, state.position(), state.lot(), null,
                state.deadlineGeneration(), state.gapGeneration());
        return applied(next, now, new LaneEvent.LaneFinished(now), true);
    }

    private LaneDecision tick(LaneState state, Instant now) {
        return switch (state.phase()) {
            case BIDDING -> now.isBefore(state.lot().deadlineAt())
                    ? LaneDecision.ignored(state, now) : closeLot(state, now);
            case GAP -> now.isBefore(state.gapEndsAt())
                    ? LaneDecision.ignored(state, now) : advanceAfterGap(state, now);
            case READY, PAUSED_BIDDING, PAUSED_GAP, FINISHED -> LaneDecision.ignored(state, now);
        };
    }

    private LaneDecision applied(LaneState next, Instant now, LaneEvent event, boolean persist,
            LaneEffect.ScheduleTimer... timers) {
        var effects = new ArrayList<LaneEffect>();
        if (persist) effects.add(new LaneEffect.PersistChange(event));
        effects.add(new LaneEffect.PublishEvent(event));
        effects.addAll(List.of(timers));
        return new LaneDecision(next, now, LaneDecision.Outcome.APPLIED, null, null, effects);
    }
}
