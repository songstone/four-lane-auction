package com.fourlane.auction.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "lot")
@Immutable
public class SessionLot {
    @Id
    private Long id;
    @Column(name = "lane_id", nullable = false)
    private Long laneId;
    @Column(name = "item_id", nullable = false) private Long itemId;
    @Column(name = "seq_no", nullable = false) private int seqNo;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "current_price", nullable = false) private long currentPrice;
    @Column(name = "bid_count", nullable = false) private int bidCount;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)") private java.time.Instant createdAt;

    protected SessionLot() {}
}
