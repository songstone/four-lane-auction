package com.fourlane.auction.session.api;

import com.fourlane.auction.session.application.PublicSessionView;
import com.fourlane.auction.session.domain.PublicSessionCatalog;
import com.fourlane.auction.session.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class PublicSessionResponses {
    private PublicSessionResponses() {}
    @Schema(name="PublicSessionResponse", requiredProperties={"serverTime","session","lanes","me"})
    public record Detail(Instant serverTime, Session session, List<Lane> lanes, SessionMe me) {}
    @Schema(name="PublicSession", requiredProperties={"sessionId","status","laneCount","scheduledStartAt","startedAt","finishedAt","timing"})
    public record Session(long sessionId, SessionStatus status, int laneCount, Instant scheduledStartAt,
            @Schema(types={"string","null"},format="date-time") Instant startedAt,
            @Schema(types={"string","null"},format="date-time") Instant finishedAt, AdminSessionResponse.Timing timing) {}
    @Schema(name="PublicSessionMe", requiredProperties={"myLotIds"})
    public record SessionMe(List<Long> myLotIds) {}
    @Schema(name="PublicSessionLane", requiredProperties={"laneId","laneNo","snapshot","lineup"})
    public record Lane(long laneId, int laneNo, Snapshot snapshot, List<Lineup> lineup) {}
    @Schema(name="PublicLineupLot", requiredProperties={"lotId","seqNo","name","imageUrl","startPrice"})
    public record Lineup(long lotId, int seqNo, String name, String imageUrl, long startPrice) {}
    @Schema(name="PublicLaneSnapshot", requiredProperties={"laneId","epoch","seq","serverTime","heartbeatIntervalMs","state","lotInfo","nextLotInfo","me"})
    public record Snapshot(long laneId, String epoch, long seq, Instant serverTime, int heartbeatIntervalMs,
            State state, LotInfo lotInfo, LotInfo nextLotInfo, LaneMe me) {}
    @Schema(name="PublicLaneMe", requiredProperties={"isHighestBidder"})
    public record LaneMe(boolean isHighestBidder) {}
    @Schema(name="PublicLotInfo", requiredProperties={"lotId","name","imageUrl","startPrice"})
    public record LotInfo(long lotId, String name, String imageUrl, long startPrice) {}
    @Schema(name="PublicLaneState", requiredProperties={"laneStatus","position","remainingLots","gapEndsAt","pauseCause","lot"})
    public record State(String laneStatus, int position, int remainingLots,
            @Schema(types={"string","null"},format="date-time") Instant gapEndsAt,
            @Schema(types={"string","null"}) String pauseCause, LotState lot) {}
    @Schema(name="PublicLotState", requiredProperties={"lotId","status","currentPrice","nextPrice","bidCount","lastBidId","deadlineAt","pausedRemainingMs","highestBidder","reserveNotMet"})
    public record LotState(long lotId, String status, long currentPrice,
            @Schema(types={"integer","null"},format="int64") Long nextPrice, int bidCount,
            @Schema(types={"integer","null"},format="int64") Long lastBidId,
            @Schema(types={"string","null"},format="date-time") Instant deadlineAt,
            @Schema(types={"integer","null"},format="int32") Integer pausedRemainingMs,
            @Schema(types={"string","null"}) String highestBidder, boolean reserveNotMet) {}
    @Schema(name="PublicLotDetail", requiredProperties={"lotId","sessionId","laneId","laneNo","seqNo","status","name","description","images","startPrice","finalPrice","closedAt"})
    public record LotDetail(long lotId, long sessionId, long laneId, int laneNo, int seqNo, String status,
            String name, String description, List<Image> images, long startPrice,
            @Schema(types={"integer","null"},format="int64") Long finalPrice,
            @Schema(types={"string","null"},format="date-time") Instant closedAt) {}
    @Schema(name="PublicLotImage", requiredProperties={"url","sortOrder"})
    public record Image(String url, int sortOrder) {}

    public static Detail from(PublicSessionView view) {
        var h = view.header();
        var grouped = new LinkedHashMap<Long, List<PublicSessionCatalog.Row>>();
        view.rows().forEach(r -> grouped.computeIfAbsent(r.getLaneId(), ignored -> new ArrayList<>()).add(r));
        var lanes = grouped.values().stream().map(rows -> {
            var first = rows.getFirst();
            var lineup = rows.stream().filter(r -> r.getLotId() != null)
                    .map(r -> new Lineup(r.getLotId(), r.getSeqNo(), r.getName(), imageUrl(r.getStorageKey()), r.getStartPrice())).toList();
            var lot = lineup.isEmpty() ? null : lineup.getFirst();
            var state = new State("READY", lot == null ? 0 : 1, lot == null ? 0 : lineup.size()-1, null, null,
                    lot == null ? null : new LotState(lot.lotId(), "PENDING", lot.startPrice(), null, 0, null, null, null, null, false));
            var snapshot = new Snapshot(first.getLaneId(), view.epoch(), 0, view.serverTime(), 5000, state,
                    lot == null ? null : new LotInfo(lot.lotId(), lot.name(), lot.imageUrl(), lot.startPrice()),
                    null, view.myLotIds() == null ? null : new LaneMe(false));
            return new Lane(first.getLaneId(), first.getLaneNo(), snapshot, lineup);
        }).toList();
        return new Detail(view.serverTime(), new Session(h.getSessionId(), h.getStatus(), h.getLaneCount(),
                h.getScheduledStartAt(), h.getStartedAt(), h.getFinishedAt(),
                new AdminSessionResponse.Timing(h.getFirstBidWaitMs(), h.getSoftCloseMs(), h.getLotGapMs(), h.getResumeMinMs())),
                lanes, view.myLotIds() == null ? null : new SessionMe(view.myLotIds()));
    }

    public static LotDetail from(PublicSessionView.Lot view) {
        var l = view.detail();
        Long finalPrice = "SOLD".equals(l.getStatus()) || ("UNSOLD".equals(l.getStatus()) && l.getBidCount() > 0)
                ? l.getCurrentPrice() : null;
        return new LotDetail(l.getLotId(), l.getSessionId(), l.getLaneId(), l.getLaneNo(), l.getSeqNo(), l.getStatus(),
                l.getName(), l.getDescription(), view.images().stream().map(i -> new Image(imageUrl(i.getStorageKey()), i.getSortOrder())).toList(),
                l.getStartPrice(), finalPrice, l.getClosedAt());
    }
    private static String imageUrl(String key) { return key == null ? null : "/api/images/" + key; }
}
