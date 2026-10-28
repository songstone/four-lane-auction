package com.fourlane.engine.lane;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** R13 calculation only. The driver owns serialization and storage of the previous processing time. */
public final class LaneProcessingTime {
    private LaneProcessingTime() {}

    /** Read exactly once AFTER serialization, including for rejected or ignored commands. */
    public static Instant read(Clock clock, Instant previous) {
        var observed = requireMillis(Objects.requireNonNull(clock).instant());
        if (previous == null) return observed;
        requireMillis(previous);
        return observed.isBefore(previous) ? previous : observed;
    }

    static Instant requireMillis(Instant at) {
        Objects.requireNonNull(at, "time");
        if (at.getNano() % 1_000_000 != 0)
            throw new IllegalArgumentException("Time must have millisecond precision");
        return at;
    }
}
