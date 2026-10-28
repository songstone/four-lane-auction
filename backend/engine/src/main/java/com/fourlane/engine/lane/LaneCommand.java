package com.fourlane.engine.lane;
import java.time.Instant;
public sealed interface LaneCommand {
    record Activate(Instant startedAt) implements LaneCommand {}
    record PlaceBid(long lotId, long bidderId, long expectedPrice, long amount) implements LaneCommand {}
    record CheckDeadline(long lotId, long generation) implements LaneCommand {}
    record EndGap(long lotId, long generation) implements LaneCommand {}
    record Tick() implements LaneCommand {}
}
