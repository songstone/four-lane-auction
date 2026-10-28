package com.fourlane.engine.lane;
import com.fourlane.engine.policy.BidIncrementPolicy;
import java.time.Instant;
import java.util.List;
public record LaneState(LanePhase phase, List<LotPlan> plans, int position, LotState lot,
        Instant gapEndsAt, long deadlineGeneration, long gapGeneration,
        LaneTiming timing, BidIncrementPolicy bidPolicy) {}
