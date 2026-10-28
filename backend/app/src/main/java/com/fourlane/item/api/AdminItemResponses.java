package com.fourlane.item.api;

import com.fourlane.item.application.*;
import com.fourlane.item.domain.ItemReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class AdminItemResponses {
    private AdminItemResponses() {}
    @Schema(name = "AdminItemSummary", requiredProperties = {"itemId", "name", "imageUrl", "startPrice", "sellerNickname", "reviewStatus", "stage", "createdAt", "reviewedAt"})
    public record Summary(long itemId, String name, String imageUrl, long startPrice, String sellerNickname,
            ItemReviewStatus reviewStatus, ItemResponses.Stage stage, Instant createdAt,
            @Schema(types = {"string", "null"}, format = "date-time") Instant reviewedAt) {
        static Summary from(AdminItemView v) { return new Summary(v.itemId(), v.name(), v.imageUrl(), v.startPrice(), v.sellerNickname(), v.reviewStatus(), ItemResponses.Stage.valueOf(v.stage()), v.createdAt(), v.reviewedAt()); }
    }
    @Schema(name = "AdminItemListResponse", requiredProperties = {"items", "nextCursor"})
    public record Page(List<Summary> items, @Schema(types = {"string", "null"}) String nextCursor) {
        static Page from(AdminItemQueryService.Page p) { return new Page(p.items().stream().map(Summary::from).toList(), p.nextCursor()); }
    }
    @Schema(name = "AdminItemImage", requiredProperties = {"url", "sortOrder"})
    public record Image(String url, int sortOrder) {}
    @Schema(name = "AdminItemLot", requiredProperties = {"lotId", "sessionId", "laneNo", "seqNo", "status", "finalPrice"})
    public record Lot(long lotId, long sessionId, int laneNo, int seqNo, String status,
            @Schema(types = {"integer", "null"}, format = "int64") Long finalPrice) {}
    @Schema(name = "AdminItemDetail", requiredProperties = {"itemId", "name", "description", "images", "startPrice", "reservePrice", "sellerNickname", "reviewStatus", "stage", "rejectReason", "createdAt", "reviewedAt", "lots"})
    public record Detail(long itemId, String name, @Schema(types = {"string", "null"}) String description,
            List<Image> images, long startPrice, long reservePrice, String sellerNickname,
            ItemReviewStatus reviewStatus, ItemResponses.Stage stage,
            @Schema(types = {"string", "null"}) String rejectReason, Instant createdAt,
            @Schema(types = {"string", "null"}, format = "date-time") Instant reviewedAt, List<Lot> lots) {
        static Detail from(AdminItemView.Detail d) {
            var v = d.item();
            return new Detail(v.itemId(), v.name(), d.description(), d.images().stream().map(i -> new Image(i.url(), i.sortOrder())).toList(),
                    v.startPrice(), d.reservePrice(), v.sellerNickname(), v.reviewStatus(), ItemResponses.Stage.valueOf(v.stage()), d.rejectReason(), v.createdAt(), v.reviewedAt(),
                    d.lots().stream().map(l -> new Lot(l.lotId(), l.sessionId(), l.laneNo(), l.seqNo(), l.status(), l.finalPrice())).toList());
        }
    }
    @Schema(name = "ItemReviewResponse", requiredProperties = {"itemId", "reviewStatus", "reviewedAt"})
    public record Reviewed(long itemId, ItemReviewStatus reviewStatus, Instant reviewedAt) {
        static Reviewed from(ItemReviewService.Result r) { return new Reviewed(r.itemId(), r.reviewStatus(), r.reviewedAt()); }
    }
}
