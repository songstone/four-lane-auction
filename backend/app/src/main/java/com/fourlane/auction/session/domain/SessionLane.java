package com.fourlane.auction.session.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="lane")
public class SessionLane {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="session_id",nullable=false) private Long sessionId;
    @Column(name="lane_no",nullable=false,columnDefinition="TINYINT") private int laneNo;
    @Column(nullable=false,length=20) private String status;
    @Column(name="paused_remaining_ms") private Integer pausedRemainingMs;
    @Column(name="pause_cause",length=20) private String pauseCause;
    @Column(name="finished_at",columnDefinition="DATETIME(3)") private Instant finishedAt;
    @Column(name="created_at",nullable=false,columnDefinition="DATETIME(3)") private Instant createdAt;
    @Column(name="updated_at",nullable=false,columnDefinition="DATETIME(3)") private Instant updatedAt;
    protected SessionLane() {}
    public SessionLane(long sessionId,int laneNo,Instant at) {
        if (laneNo<1 || laneNo>4) throw new IllegalArgumentException("Lane number");
        this.sessionId=sessionId; this.laneNo=laneNo; status="READY"; createdAt=at; updatedAt=at;
    }
}
