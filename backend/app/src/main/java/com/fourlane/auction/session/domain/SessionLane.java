package com.fourlane.auction.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "lane")
@Immutable
public class SessionLane {
    @Id
    private Long id;
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    protected SessionLane() {}
}
