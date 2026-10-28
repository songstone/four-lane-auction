package com.fourlane.auction.session.domain;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="auction_session")
public class AuctionSession {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=20) private SessionStatus status;
    @Column(name="lane_count",nullable=false) private Byte laneCount;
    @Column(name="scheduled_start_at",nullable=false,columnDefinition="DATETIME(3)") private Instant scheduledStartAt;
    @Column(name="first_bid_wait_ms",nullable=false) private int firstBidWaitMs;
    @Column(name="soft_close_ms",nullable=false) private int softCloseMs;
    @Column(name="lot_gap_ms",nullable=false) private int lotGapMs;
    @Column(name="resume_min_ms",nullable=false) private int resumeMinMs;
    @Column(name="bid_policy_id") private Long bidPolicyId;
    @Column(name="started_at",columnDefinition="DATETIME(3)") private Instant startedAt;
    @Column(name="finished_at",columnDefinition="DATETIME(3)") private Instant finishedAt;
    @Column(name="created_at",nullable=false,columnDefinition="DATETIME(3)") private Instant createdAt;
    @Column(name="updated_at",nullable=false,columnDefinition="DATETIME(3)") private Instant updatedAt;
    protected AuctionSession() {}
    public AuctionSession(int lanes, Instant scheduled, SessionTiming timing, Instant at) {
        if (lanes<1 || lanes>4) throw new IllegalArgumentException("Lane count");
        laneCount=(byte)lanes; status=SessionStatus.SCHEDULED; scheduledStartAt=scheduled;
        setTiming(timing); createdAt=at; updatedAt=at;
    }
    private void setTiming(SessionTiming t) {
        firstBidWaitMs=(int)t.firstBidWait().toMillis(); softCloseMs=(int)t.softClose().toMillis();
        lotGapMs=(int)t.lotGap().toMillis(); resumeMinMs=(int)t.resumeMin().toMillis();
    }
    public void revise(Instant scheduled, SessionTiming timing, Instant at) {
        if (status!=SessionStatus.SCHEDULED) throw new IllegalStateException("Session already started");
        if (!scheduledStartAt.equals(scheduled) || !timing().equals(timing)) {
            scheduledStartAt=scheduled; setTiming(timing); updatedAt=at;
        }
    }
    public long id() { return id; }
    public SessionStatus status() { return status; }
    public Instant scheduledStartAt() { return scheduledStartAt; }
    public SessionTiming timing() { return SessionTiming.milliseconds(firstBidWaitMs,softCloseMs,lotGapMs,resumeMinMs); }
}
