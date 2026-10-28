package com.fourlane.engine.lane;

import java.time.Duration;

/** The four independent duration bounds approved in OQ-14. */
public record LaneTiming(Duration firstBidWait, Duration softClose, Duration lotGap, Duration resumeMin) {
    public LaneTiming {
        for (var duration : new Duration[]{firstBidWait, softClose, lotGap, resumeMin})
            if (duration == null || duration.compareTo(Duration.ofSeconds(1)) < 0
                    || duration.compareTo(Duration.ofSeconds(60)) > 0 || duration.getNano() % 1_000_000 != 0)
                throw new IllegalArgumentException("Duration must be 1000..60000 whole milliseconds");
    }
}
