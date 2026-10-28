package com.fourlane.auction.session.application;
import com.fourlane.auction.session.domain.SessionStatus;
import java.time.Instant;
import java.util.List;
public record AdminSessionView(Session session, boolean startable, List<Integer> emptyLaneNos,List<Lane> lanes) {
    public record Session(long sessionId,SessionStatus status,int laneCount,Instant scheduledStartAt,Instant startedAt,
        Instant finishedAt,Timing timing,Integer bidPolicyVersion){}
    public record Timing(int firstBidWaitMs,int softCloseMs,int lotGapMs,int resumeMinMs){}
    public record Lane(long laneId,int laneNo,String laneStatus,List<Lot> lots){}
    public record Lot(long lotId,int seqNo,long itemId,String name,String imageUrl,long startPrice,String sellerNickname,String status){}
}
