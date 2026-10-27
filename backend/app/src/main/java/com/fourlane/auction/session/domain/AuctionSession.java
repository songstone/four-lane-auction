package com.fourlane.auction.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// M0 read mapping only. Creation and state transitions belong to later milestones.
@Entity
@Table(name = "auction_session")
@Immutable
public class AuctionSession {
    @Id
    private Long id;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SessionStatus status;
    @Column(name = "lane_count", nullable = false)
    private Byte laneCount;
    @Column(name = "scheduled_start_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant scheduledStartAt;
    @Column(name = "started_at", columnDefinition = "DATETIME(3)")
    private Instant startedAt;
    @Column(name = "finished_at", columnDefinition = "DATETIME(3)")
    private Instant finishedAt;

    protected AuctionSession() {}
}
