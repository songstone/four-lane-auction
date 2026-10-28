package com.fourlane.auction.policy.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "bid_increment_policy")
@Immutable
public class BidPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private int version;
    @Column(name = "created_by", updatable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(3)") private Instant createdAt;

    protected BidPolicy() {}
    public BidPolicy(int version, long createdBy, Instant createdAt) {
        this.version = version; this.createdBy = createdBy; this.createdAt = createdAt;
    }
    public long id() { return id; }
}
