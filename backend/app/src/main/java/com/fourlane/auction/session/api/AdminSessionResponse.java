package com.fourlane.auction.session.api;
import com.fourlane.auction.session.application.AdminSessionView;
import com.fourlane.auction.session.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
@Schema(requiredProperties={"session","startable","emptyLaneNos","lanes"})
public record AdminSessionResponse(Session session,boolean startable,List<Integer> emptyLaneNos,List<Lane> lanes){
    @Schema(name="AdminSession",requiredProperties={"sessionId","status","laneCount","scheduledStartAt","startedAt","finishedAt","timing","bidPolicyVersion"})
    public record Session(long sessionId,SessionStatus status,int laneCount,Instant scheduledStartAt,
        @Schema(types={"string","null"},format="date-time") Instant startedAt,
        @Schema(types={"string","null"},format="date-time") Instant finishedAt,Timing timing,
        @Schema(types={"integer","null"},format="int32") Integer bidPolicyVersion){}
    @Schema(name="SessionTiming",requiredProperties={"firstBidWaitMs","softCloseMs","lotGapMs","resumeMinMs"})
    public record Timing(int firstBidWaitMs,int softCloseMs,int lotGapMs,int resumeMinMs){}
    @Schema(name="AdminSessionLane",requiredProperties={"laneId","laneNo","laneStatus","lots"})
    public record Lane(long laneId,int laneNo,String laneStatus,List<Lot> lots){}
    @Schema(name="AdminSessionLot",requiredProperties={"lotId","seqNo","itemId","name","imageUrl","startPrice","sellerNickname","status"})
    public record Lot(long lotId,int seqNo,long itemId,String name,String imageUrl,long startPrice,String sellerNickname,String status){}
    public static Lane lane(AdminSessionView.Lane l) {
        return new Lane(l.laneId(), l.laneNo(), l.laneStatus(), l.lots().stream()
            .map(o -> new Lot(o.lotId(), o.seqNo(), o.itemId(), o.name(), o.imageUrl(), o.startPrice(), o.sellerNickname(), o.status())).toList());
    }
    public static AdminSessionResponse from(AdminSessionView v){
        var s=v.session();var t=s.timing();
        return new AdminSessionResponse(new Session(s.sessionId(),s.status(),s.laneCount(),s.scheduledStartAt(),s.startedAt(),s.finishedAt(),
            new Timing(t.firstBidWaitMs(),t.softCloseMs(),t.lotGapMs(),t.resumeMinMs()),s.bidPolicyVersion()),v.startable(),v.emptyLaneNos(),
            v.lanes().stream().map(AdminSessionResponse::lane).toList());
    }
}
