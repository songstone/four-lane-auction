package com.fourlane.auction.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
@Table(name = "lot")
public class SessionLot {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name = "lane_id", nullable = false)
    private Long laneId;
    @Column(name = "item_id", nullable = false) private Long itemId;
    @Column(name = "seq_no", nullable = false) private int seqNo;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "current_price", nullable = false) private long currentPrice;
    @Column(name = "bid_count", nullable = false) private int bidCount;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)") private java.time.Instant createdAt;

    @Column(name="start_price",nullable=false) private long startPrice;
    @Column(name="reserve_price",nullable=false) private long reservePrice;
    @Column(name="highest_bidder_id") private Long highestBidderId;
    @Column(name="last_bid_id") private Long lastBidId;
    @Column(name="bidding_started_at",columnDefinition="DATETIME(3)") private java.time.Instant biddingStartedAt;
    @Column(name="deadline_at",columnDefinition="DATETIME(3)") private java.time.Instant deadlineAt;
    @Column(name="closed_at",columnDefinition="DATETIME(3)") private java.time.Instant closedAt;
    @Column(name="unsold_reason",length=20) private String unsoldReason;
    @Column(name="updated_at",nullable=false,columnDefinition="DATETIME(3)") private java.time.Instant updatedAt;
    protected SessionLot() {}
}
