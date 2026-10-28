package com.fourlane.auction.policy.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "bid_increment_tier")
@Immutable
public class BidPolicyTier {
    @Embeddable public static class Key implements Serializable {
        @Column(name = "policy_id", updatable = false) private long policyId;
        @Column(name = "min_price", updatable = false) private long minPrice;
        protected Key() {}
        Key(long policyId, long minPrice) { this.policyId = policyId; this.minPrice = minPrice; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && policyId == key.policyId && minPrice == key.minPrice;
        }
        @Override public int hashCode() { return Objects.hash(policyId, minPrice); }
    }
    @EmbeddedId private Key id;
    @Column(nullable = false, updatable = false) private long increment;
    protected BidPolicyTier() {}
    public BidPolicyTier(long policyId, long minPrice, long increment) {
        this.id = new Key(policyId, minPrice); this.increment = increment;
    }
}
