package com.fourlane.item.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "item")
public class Item {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "seller_id", nullable = false) private Long sellerId;
    @Column(nullable = false, length = 100) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "start_price", nullable = false) private long startPrice;
    @Column(name = "reserve_price", nullable = false) private long reservePrice;
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false, length = 20)
    private ItemReviewStatus reviewStatus;
    @Column(name = "reject_reason", length = 500) private String rejectReason;
    @Column(name = "reviewed_at", columnDefinition = "DATETIME(3)") private Instant reviewedAt;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)") private Instant createdAt;
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)") private Instant updatedAt;

    protected Item() {}
    public Item(long sellerId, String name, String description, long startPrice, long reservePrice, Instant now) {
        this.sellerId = sellerId;
        this.name = name;
        this.description = description;
        this.startPrice = startPrice;
        this.reservePrice = reservePrice;
        this.reviewStatus = ItemReviewStatus.PENDING_REVIEW;
        this.createdAt = now;
        this.updatedAt = now;
    }
    public Long id() { return id; }
    public String name() { return name; }
    public ItemReviewStatus reviewStatus() { return reviewStatus; }
    public Instant createdAt() { return createdAt; }
}
