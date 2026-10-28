package com.fourlane.auction.policy.domain;

import java.time.Instant;
import java.util.List;

public interface BidPolicyCatalog {
    interface Row {
        long getPolicyId();
        int getVersion();
        Instant getCreatedAt();
        String getCreatedByNickname();
        long getMinPrice();
        long getIncrement();
    }
    List<Row> all();
    List<Row> byId(long policyId);
}
