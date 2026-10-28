package com.fourlane.item.application;

import com.fourlane.item.domain.ItemReviewStatus;
import java.time.Instant;

public record ItemView(long itemId, String name, String imageUrl, long startPrice,
        ItemReviewStatus reviewStatus, String rejectReason, Instant reviewedAt, Instant createdAt, String stage, CurrentLot currentLot) {
    public record CurrentLot(long lotId, long sessionId, int laneNo, int seqNo, String status,
            Instant scheduledStartAt, Long finalPrice, String tradeStatus) {}
}
