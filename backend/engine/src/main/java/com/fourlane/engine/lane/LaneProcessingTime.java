package com.fourlane.engine.lane;
import java.time.Clock;
import java.time.Instant;
public final class LaneProcessingTime {
    private LaneProcessingTime() {}
    public static Instant read(Clock clock, Instant previous) {
        return clock.instant(); // RED scaffold; R13 is implemented after the race tests fail.
    }
}
