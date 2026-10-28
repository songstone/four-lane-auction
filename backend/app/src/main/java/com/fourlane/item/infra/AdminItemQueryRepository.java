package com.fourlane.item.infra;

import com.fourlane.item.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface AdminItemQueryRepository extends Repository<Item, Long> {
    String STAGE = """
        case when i.reviewStatus = com.fourlane.item.domain.ItemReviewStatus.PENDING_REVIEW then 'PENDING_REVIEW'
             when i.reviewStatus = com.fourlane.item.domain.ItemReviewStatus.REJECTED then 'REJECTED'
             when lot.status = 'PENDING' then 'SCHEDULED' when lot.status = 'BIDDING' then 'ON_AUCTION'
             when lot.status = 'SOLD' then 'SOLD' when lot.status = 'UNSOLD' then 'UNSOLD' else 'APPROVED' end
        """;
    String COLUMNS = """
        i.id as itemId, i.name as name, image.storageKey as storageKey, i.startPrice as startPrice,
        seller.nickname as sellerNickname, i.reviewStatus as reviewStatus, i.createdAt as createdAt,
        i.reviewedAt as reviewedAt,
        """ + STAGE + " as stage ";
    String FROM = """
        from Item i join Member seller on seller.id = i.sellerId
        join ItemImage image on image.itemId = i.id and image.sortOrder = 0
        left join SessionLot lot on lot.itemId = i.id and lot.createdAt >= i.reviewedAt
            and not exists (select newer.id from SessionLot newer where newer.itemId = i.id
                and newer.createdAt >= i.reviewedAt
                and (newer.createdAt > lot.createdAt or (newer.createdAt = lot.createdAt and newer.id > lot.id)))
        """;
    @Query("select " + COLUMNS + FROM + " where " + STAGE + " = :stage " + """
        and (:afterTime is null or i.createdAt > :afterTime or (i.createdAt = :afterTime and i.id > :afterId))
        order by i.createdAt asc, i.id asc
        """)
    List<AdminItemCatalog.Row> ascending(String stage, Instant afterTime, long afterId, Pageable page);
    @Query("select " + COLUMNS + FROM + " where " + STAGE + " = :stage " + """
        and (:afterTime is null or i.createdAt < :afterTime or (i.createdAt = :afterTime and i.id < :afterId))
        order by i.createdAt desc, i.id desc
        """)
    List<AdminItemCatalog.Row> descending(String stage, Instant afterTime, long afterId, Pageable page);
    @Query("select " + COLUMNS + ", i.description as description, i.reservePrice as reservePrice, i.rejectReason as rejectReason " + FROM + " where i.id = :id")
    Optional<AdminItemCatalog.DetailRow> detail(long id);
    @Query("select image.storageKey as storageKey, image.sortOrder as sortOrder from ItemImage image where image.itemId = :id order by image.sortOrder")
    List<AdminItemCatalog.ImageRow> images(long id);
    @Query("""
        select lot.id as lotId, lane.sessionId as sessionId, lane.laneNo as laneNo, lot.seqNo as seqNo,
               lot.status as status,
               case when lot.status in ('SOLD', 'UNSOLD') and lot.bidCount > 0 then lot.currentPrice else null end as finalPrice
        from SessionLot lot join SessionLane lane on lane.id = lot.laneId
        where lot.itemId = :id order by lot.createdAt desc, lot.id desc
        """)
    List<AdminItemCatalog.LotRow> lots(long id);
}
