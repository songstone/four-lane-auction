package com.fourlane.item.infra;

/** Shared by seller, admin filtering and assignment validation. No stored stage column. */
public final class ItemStageQuery {
    private ItemStageQuery() {}
    public static final String STAGE = """
        case when i.reviewStatus = com.fourlane.item.domain.ItemReviewStatus.PENDING_REVIEW then 'PENDING_REVIEW'
             when i.reviewStatus = com.fourlane.item.domain.ItemReviewStatus.REJECTED then 'REJECTED'
             when lot.status = 'PENDING' then 'SCHEDULED' when lot.status = 'BIDDING' then 'ON_AUCTION'
             when lot.status = 'SOLD' then 'SOLD' when lot.status = 'UNSOLD' then 'UNSOLD' else 'APPROVED' end
        """;
    public static final String LATEST_LOT = """
        left join SessionLot lot on lot.itemId = i.id and lot.createdAt >= i.reviewedAt
            and not exists (select newer.id from SessionLot newer where newer.itemId = i.id
                and newer.createdAt >= i.reviewedAt
                and (newer.createdAt > lot.createdAt or (newer.createdAt = lot.createdAt and newer.id > lot.id)))
        """;
}
