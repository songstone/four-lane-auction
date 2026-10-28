package com.fourlane.item.infra;

import com.fourlane.item.domain.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ItemQueryRepository extends Repository<Item, Long> {
    @Query("""
        select i.id as itemId, i.name as name, image.storageKey as storageKey,
               i.startPrice as startPrice, i.reviewStatus as reviewStatus,
               i.rejectReason as rejectReason, i.reviewedAt as reviewedAt, i.createdAt as createdAt
        from Item i join ItemImage image on image.itemId = i.id and image.sortOrder = 0
        where i.sellerId = :sellerId and (:afterTime is null or i.createdAt < :afterTime
               or (i.createdAt = :afterTime and i.id < :afterId))
        order by i.createdAt desc, i.id desc
        """)
    List<ItemCatalog.Row> list(@Param("sellerId") long sellerId, @Param("afterTime") Instant afterTime,
            @Param("afterId") long afterId, Pageable pageable);
}
