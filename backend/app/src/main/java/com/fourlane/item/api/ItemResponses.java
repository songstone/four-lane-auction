package com.fourlane.item.api;

import com.fourlane.item.application.*;
import com.fourlane.item.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class ItemResponses {
    private ItemResponses() {}
    public enum Stage { PENDING_REVIEW, REJECTED, APPROVED, SCHEDULED, ON_AUCTION, SOLD, UNSOLD }
    @Schema(name = "ItemRegistrationResponse")
    public record Created(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long itemId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemReviewStatus reviewStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
        public static Created from(Item item) { return new Created(item.id(), item.name(), item.reviewStatus(), item.createdAt()); }
    }
    @Schema(name = "MyItemListResponse")
    public record Page(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Summary> items,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String nextCursor) {
        public static Page from(ItemQueryService.Result result) { return new Page(result.items().stream().map(Summary::from).toList(), result.nextCursor()); }
    }
    @Schema(name = "MyItemSummary")
    public record Summary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long itemId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String imageUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long startPrice,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemReviewStatus reviewStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Stage stage,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String rejectReason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}, format = "date-time") Instant reviewedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CurrentLot currentLot) {
        static Summary from(ItemView item) {
            return new Summary(item.itemId(), item.name(), item.imageUrl(), item.startPrice(), item.reviewStatus(),
                    Stage.valueOf(item.stage()), item.rejectReason(), item.reviewedAt(), item.createdAt(),
                    item.currentLot() == null ? null : new CurrentLot(item.currentLot().lotId(), item.currentLot().sessionId(),
                        item.currentLot().laneNo(), item.currentLot().seqNo(), item.currentLot().status(),
                        item.currentLot().scheduledStartAt(), item.currentLot().finalPrice(), item.currentLot().tradeStatus()));
        }
    }
    @Schema(name = "MyItemCurrentLot")
    public record CurrentLot(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long lotId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int laneNo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int seqNo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant scheduledStartAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"integer", "null"}, format = "int64") Long finalPrice,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String tradeStatus) {}
}
