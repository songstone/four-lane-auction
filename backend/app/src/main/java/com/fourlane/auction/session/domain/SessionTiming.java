package com.fourlane.auction.session.domain;
import java.time.Duration;
import java.util.Map;
public record SessionTiming(Duration firstBidWait, Duration softClose, Duration lotGap, Duration resumeMin) {
    public SessionTiming {
        for (var value : new Duration[]{firstBidWait, softClose, lotGap, resumeMin})
            if (value == null || value.toNanos() % 1_000_000 != 0 || value.toMillis() < 1000 || value.toMillis() > 60000)
                throw new IllegalArgumentException("Timing outside approved bounds");
    }
    public static SessionTiming milliseconds(int first, int soft, int gap, int resume) {
        return new SessionTiming(Duration.ofMillis(first), Duration.ofMillis(soft), Duration.ofMillis(gap), Duration.ofMillis(resume));
    }
    public SessionTiming with(Map<String,Integer> values) {
        return milliseconds(values.getOrDefault("firstBidWaitMs", (int)firstBidWait.toMillis()),
            values.getOrDefault("softCloseMs", (int)softClose.toMillis()), values.getOrDefault("lotGapMs", (int)lotGap.toMillis()),
            values.getOrDefault("resumeMinMs", (int)resumeMin.toMillis()));
    }
}
