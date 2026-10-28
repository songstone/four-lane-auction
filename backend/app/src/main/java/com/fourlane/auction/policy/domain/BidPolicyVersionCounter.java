package com.fourlane.auction.policy.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "bid_policy_version_counter")
public class BidPolicyVersionCounter {
    @Id private Integer id;
    @Column(name = "last_version", nullable = false) private int lastVersion;
    protected BidPolicyVersionCounter() {}
    public int allocate() { lastVersion = Math.incrementExact(lastVersion); return lastVersion; }
}
