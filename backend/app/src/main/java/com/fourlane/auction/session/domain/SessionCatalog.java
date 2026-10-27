package com.fourlane.auction.session.domain;

import java.time.Instant;
import java.util.List;

public interface SessionCatalog {
    List<SessionListRow> list(SessionStatus status, boolean firstPage, Instant afterTime, long afterId, int limit);
}
