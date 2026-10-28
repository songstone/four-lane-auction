package com.fourlane.auction.session.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Public read projections deliberately exclude reserve prices and unsold reasons. */
public interface PublicSessionCatalog {
    Optional<Header> header(long id);
    List<Row> rows(long sessionId);
    Optional<Lot> lot(long lotId);
    List<Image> images(long lotId);
    interface Header {
        long getSessionId();
        SessionStatus getStatus();
        int getLaneCount();
        Instant getScheduledStartAt();
        Instant getStartedAt();
        Instant getFinishedAt();
        int getFirstBidWaitMs();
        int getSoftCloseMs();
        int getLotGapMs();
        int getResumeMinMs();
    }
    interface Row {
        long getLaneId();
        int getLaneNo();
        String getLaneStatus();
        Long getLotId();
        Integer getSeqNo();
        String getName();
        String getStorageKey();
        Long getStartPrice();
        Long getSellerId(); // Internal identity comparison only; never serialized.
        String getLotStatus();
    }
    interface Lot {
        long getLotId();
        long getSessionId();
        long getLaneId();
        int getLaneNo();
        int getSeqNo();
        String getStatus();
        String getName();
        String getDescription();
        long getStartPrice();
        long getCurrentPrice();
        int getBidCount();
        Instant getClosedAt();
    }
    interface Image {
        String getStorageKey();
        int getSortOrder();
    }
}
