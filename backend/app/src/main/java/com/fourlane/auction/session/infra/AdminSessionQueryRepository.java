package com.fourlane.auction.session.infra;
import com.fourlane.auction.session.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
public interface AdminSessionQueryRepository extends Repository<AuctionSession,Long> {
    @Query("""
        select s.id as sessionId,s.status as status,s.laneCount as laneCount,s.scheduledStartAt as scheduledStartAt,
               s.startedAt as startedAt,s.finishedAt as finishedAt,s.firstBidWaitMs as firstBidWaitMs,
               s.softCloseMs as softCloseMs,s.lotGapMs as lotGapMs,s.resumeMinMs as resumeMinMs,p.version as bidPolicyVersion
        from AuctionSession s left join BidPolicy p on p.id=s.bidPolicyId where s.id=:id
        """)
    Optional<AdminSessionCatalog.Header> header(long id);
    @Query("""
        select l.id as laneId,l.laneNo as laneNo,l.status as laneStatus,o.id as lotId,o.seqNo as seqNo,
               o.itemId as itemId,i.name as name,im.storageKey as storageKey,o.startPrice as startPrice,
               m.nickname as sellerNickname,o.status as status
        from SessionLane l left join SessionLot o on o.laneId=l.id
        left join Item i on i.id=o.itemId left join Member m on m.id=i.sellerId
        left join ItemImage im on im.itemId=i.id and im.sortOrder=0
        where l.sessionId=:id order by l.laneNo,o.seqNo
        """)
    List<AdminSessionCatalog.Row> rows(long id);
}
