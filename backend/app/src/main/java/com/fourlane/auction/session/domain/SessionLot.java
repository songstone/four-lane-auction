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

    protected SessionLot() {}
}
