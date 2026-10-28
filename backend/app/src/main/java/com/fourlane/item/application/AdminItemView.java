package com.fourlane.item.application;

import com.fourlane.item.domain.*;
import java.time.Instant;
import java.util.List;

public record AdminItemView(long itemId, String name, String imageUrl, long startPrice, String sellerNickname,
        ItemReviewStatus reviewStatus, String stage, Instant createdAt, Instant reviewedAt) {
    static AdminItemView from(AdminItemCatalog.Row r) {
        return new AdminItemView(r.getItemId(), r.getName(), "/api/images/" + r.getStorageKey(), r.getStartPrice(),
                r.getSellerNickname(), r.getReviewStatus(), r.getStage(), r.getCreatedAt(), r.getReviewedAt());
    }
    public record Image(String url, int sortOrder) {}
    public record Lot(long lotId, long sessionId, int laneNo, int seqNo, String status, Long finalPrice) {}
    public record Detail(AdminItemView item, String description, long reservePrice, String rejectReason, List<Image> images, List<Lot> lots) {}
}
