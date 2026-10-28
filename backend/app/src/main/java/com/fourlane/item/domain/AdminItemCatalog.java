package com.fourlane.item.domain;

import java.time.Instant;
import java.util.*;

public interface AdminItemCatalog {
    interface Row {
        long getItemId(); String getName(); String getStorageKey(); long getStartPrice();
        String getSellerNickname(); ItemReviewStatus getReviewStatus(); String getStage();
        Instant getCreatedAt(); Instant getReviewedAt();
    }
    interface DetailRow extends Row { String getDescription(); long getReservePrice(); String getRejectReason(); }
    interface ImageRow { String getStorageKey(); int getSortOrder(); }
    interface LotRow {
        long getLotId(); long getSessionId(); int getLaneNo(); int getSeqNo(); String getStatus(); Long getFinalPrice();
    }
    List<Row> list(String stage, Instant afterTime, long afterId, int limit);
    Optional<DetailRow> detail(long id);
    List<ImageRow> images(long id);
    List<LotRow> lots(long id);
}
