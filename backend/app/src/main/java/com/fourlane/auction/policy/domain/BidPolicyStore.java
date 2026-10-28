package com.fourlane.auction.policy.domain;

/** Insert only: no merge, update or delete operation for committed policy rows. */
public interface BidPolicyStore {
    void insert(BidPolicy policy);
    void insert(BidPolicyTier tier);
}
