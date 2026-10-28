package com.fourlane.item.application;

import com.fourlane.global.error.*;
import com.fourlane.item.domain.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemReviewService {
    public record Result(long itemId, ItemReviewStatus reviewStatus, Instant reviewedAt) {}
    private final ItemRepository items;
    private final Clock clock;
    public ItemReviewService(ItemRepository items, Clock clock) { this.items = items; this.clock = clock; }
    @Transactional
    public Result approve(long id) {
        var item = pending(id);
        item.approve(clock.instant());
        return result(item);
    }
    @Transactional
    public Result reject(long id, String reason) {
        validateReason(reason);
        var item = pending(id);
        item.reject(reason, clock.instant());
        return result(item);
    }
    public static void validateReason(String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.invalidRequest("reason", "REQUIRED");
        if (reason.codePointCount(0, reason.length()) > 500) throw ApiException.invalidRequest("reason", "TOO_LONG");
    }
    private Item pending(long id) {
        var item = items.findForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.ITEM_NOT_FOUND));
        if (item.reviewStatus() != ItemReviewStatus.PENDING_REVIEW)
            throw ApiException.itemAlreadyReviewed(item.reviewStatus().name());
        return item;
    }
    private Result result(Item item) { return new Result(item.id(), item.reviewStatus(), item.reviewedAt()); }
}
