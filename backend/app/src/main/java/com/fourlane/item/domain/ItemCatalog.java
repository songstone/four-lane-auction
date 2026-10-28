package com.fourlane.item.domain;

import java.time.Instant;
import java.util.List;

public interface ItemCatalog {
    interface Row {
        long getItemId();
        String getName();
        String getStorageKey();
        long getStartPrice();
        ItemReviewStatus getReviewStatus();
        String getRejectReason();
        Instant getReviewedAt();
        Instant getCreatedAt();
    }
    List<Row> list(long sellerId, Instant afterTime, long afterId, int limit);
}
