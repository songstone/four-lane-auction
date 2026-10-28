package com.fourlane.item.infra;

import com.fourlane.item.domain.Item;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ItemAssignmentQueryRepository extends Repository<Item, Long> {
    interface Row {
        long getItemId(); String getStage(); Long getLaneId(); long getStartPrice();
        long getReservePrice(); Instant getReviewedAt();
    }
    @Query("select i.id as itemId, " + ItemStageQuery.STAGE + """
         as stage, lot.laneId as laneId, i.startPrice as startPrice,
         i.reservePrice as reservePrice, i.reviewedAt as reviewedAt from Item i
        """ + ItemStageQuery.LATEST_LOT + " where i.id in :ids")
    List<Row> states(Collection<Long> ids);
    @Query("select distinct o.itemId from SessionLot o where o.itemId in :ids and o.laneId <> :laneId and o.status in ('PENDING','BIDDING')")
    List<Long> occupiedElsewhere(Collection<Long> ids, long laneId);
}
