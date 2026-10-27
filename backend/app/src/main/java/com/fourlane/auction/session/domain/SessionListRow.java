package com.fourlane.auction.session.domain;

import java.time.Instant;

// Internal query projection; never serialized as a public response.
public interface SessionListRow {
    long getSessionId();
    SessionStatus getStatus();
    int getLaneCount();
    Instant getScheduledStartAt();
    Instant getStartedAt();
    Instant getFinishedAt();
    long getLotCount();

    default Instant sortTime() {
        return switch (getStatus()) {
            case SCHEDULED -> getScheduledStartAt();
            case RUNNING -> getStartedAt();
            case FINISHED -> getFinishedAt();
        };
    }
}
