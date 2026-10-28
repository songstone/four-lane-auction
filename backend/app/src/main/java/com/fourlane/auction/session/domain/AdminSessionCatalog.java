package com.fourlane.auction.session.domain;
import java.time.Instant;
import java.util.*;
public interface AdminSessionCatalog {
    Optional<Header> header(long id);
    List<Row> rows(long id);
    interface Header {
        long getSessionId(); SessionStatus getStatus(); int getLaneCount(); Instant getScheduledStartAt();
        Instant getStartedAt(); Instant getFinishedAt(); int getFirstBidWaitMs(); int getSoftCloseMs();
        int getLotGapMs(); int getResumeMinMs(); Integer getBidPolicyVersion();
    }
    interface Row {
        long getLaneId(); int getLaneNo(); String getLaneStatus(); Long getLotId(); Integer getSeqNo();
        Long getItemId(); String getName(); String getStorageKey(); Long getStartPrice(); String getSellerNickname(); String getStatus();
    }
}
