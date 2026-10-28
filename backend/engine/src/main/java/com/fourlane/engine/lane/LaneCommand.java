package com.fourlane.engine.lane;

import java.time.Instant;

/** Trusted internal commands; authentication, format checks and conditions 1..4 belong to app. */
public sealed interface LaneCommand {
    record Activate(Instant startedAt) implements LaneCommand {
        public Activate { LaneProcessingTime.requireMillis(startedAt); }
    }
    record PlaceBid(long lotId, long bidderId, long expectedPrice, long amount) implements LaneCommand {}
    record CheckDeadline(long lotId, long generation) implements LaneCommand {}
    record EndGap(long lotId, long generation) implements LaneCommand {}
    record Tick() implements LaneCommand {}
}
