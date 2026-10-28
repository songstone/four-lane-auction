package com.fourlane.auction.session.infra;

import com.fourlane.auction.session.domain.AuctionSession;
import com.fourlane.auction.session.domain.PublicSessionCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface PublicSessionQueryRepository extends Repository<AuctionSession, Long> {
    @Query("""
        select s.id as sessionId, s.status as status, s.laneCount as laneCount,
               s.scheduledStartAt as scheduledStartAt, s.startedAt as startedAt, s.finishedAt as finishedAt,
               s.firstBidWaitMs as firstBidWaitMs, s.softCloseMs as softCloseMs,
               s.lotGapMs as lotGapMs, s.resumeMinMs as resumeMinMs
        from AuctionSession s where s.id=:id
        """)
    Optional<PublicSessionCatalog.Header> header(long id);

    @Query("""
        select l.id as laneId, l.laneNo as laneNo, l.status as laneStatus,
               o.id as lotId, o.seqNo as seqNo, i.name as name, im.storageKey as storageKey,
               o.startPrice as startPrice, i.sellerId as sellerId, o.status as lotStatus
        from SessionLane l left join SessionLot o on o.laneId=l.id
        left join Item i on i.id=o.itemId
        left join ItemImage im on im.itemId=i.id and im.sortOrder=0
        where l.sessionId=:id order by l.laneNo, o.seqNo
        """)
    List<PublicSessionCatalog.Row> rows(long id);

    @Query("""
        select o.id as lotId, l.sessionId as sessionId, l.id as laneId, l.laneNo as laneNo,
               o.seqNo as seqNo, o.status as status, i.name as name, i.description as description,
               o.startPrice as startPrice, o.currentPrice as currentPrice,
               o.bidCount as bidCount, o.closedAt as closedAt
        from SessionLot o join SessionLane l on l.id=o.laneId join Item i on i.id=o.itemId
        where o.id=:id
        """)
    Optional<PublicSessionCatalog.Lot> lot(long id);

    @Query("""
        select im.storageKey as storageKey, im.sortOrder as sortOrder
        from SessionLot o join ItemImage im on im.itemId=o.itemId
        where o.id=:id order by im.sortOrder
        """)
    List<PublicSessionCatalog.Image> images(long id);
}
