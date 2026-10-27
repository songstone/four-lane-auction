package com.fourlane;

import java.time.Instant;

// Intentional M0-04 failure probe. This branch must not be merged.
final class CiTimeSourceViolationProbe {
    static Instant disallowedClockRead() {
        return Instant.now();
    }
}
