package com.fourlane.item.infra;

import com.fourlane.item.domain.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ItemQueryRepository extends Repository<Item, Long> {
    @Query("""
        select i.id as itemId, i.name as name, image.storageKey as storageKey,
               i.startPrice as startPrice, i.reviewStatus as reviewStatus,
               i.rejectReason as rejectReason, i.reviewedAt as reviewedAt, i.createdAt as createdAt,
        """ + ItemStageQuery.STAGE + """
         as stage, lot.id as lotId, lane.sessionId as sessionId, lane.laneNo as laneNo,
         lot.seqNo as seqNo, lot.status as lotStatus, session.scheduledStartAt as scheduledStartAt,
         case when lot.status in ('SOLD','UNSOLD') and lot.bidCount > 0 then lot.currentPrice else null end as finalPrice
        from Item i join ItemImage image on image.itemId = i.id and image.sortOrder = 0
        """ + ItemStageQuery.LATEST_LOT + """
        left join SessionLane lane on lane.id = lot.laneId
        left join AuctionSession session on session.id = lane.sessionId
        where i.sellerId = :sellerId and (:afterTime is null or i.createdAt < :afterTime
               or (i.createdAt = :afterTime and i.id < :afterId))
        order by i.createdAt desc, i.id desc
        """)
    List<ItemCatalog.Row> list(long sellerId, Instant afterTime, long afterId, Pageable pageable);
}
