package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.global.error.ApiException;
import com.fourlane.item.application.ItemReviewService;
import com.fourlane.item.application.AdminItemCursorCodec;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ItemReviewValidationTest {
    @Test void validatesCodePointsAndPreservesInput() {
        ItemReviewService.validateReason("😀".repeat(500));
        ItemReviewService.validateReason("  확인 불가  ");
        for (String input : new String[]{null, "", " \t\n"}) {
            var error = catchThrowableOfType(ApiException.class, () -> ItemReviewService.validateReason(input));
            assertThat(error.getFields().getFirst().reason()).isEqualTo("REQUIRED");
        }
        var error = catchThrowableOfType(ApiException.class, () -> ItemReviewService.validateReason("😀".repeat(501)));
        assertThat(error.getFields().getFirst().reason()).isEqualTo("TOO_LONG");
    }
    @Test void bindsCursorToStageAndRejectsInvalidPositions() {
        var codec = new AdminItemCursorCodec();
        var time = Instant.parse("2026-10-28T01:02:03.456Z");
        var cursor = codec.encode("PENDING_REVIEW", time, 1);
        assertThat(codec.decode(cursor, "PENDING_REVIEW")).isEqualTo(new AdminItemCursorCodec.Position(time, 1));
        for (String invalid : new String[]{"", "!", codec.encode("APPROVED", time, 1),
                codec.encode("PENDING_REVIEW", time, 0), codec.encode("PENDING_REVIEW", time.plusNanos(1), 1)})
            assertThatThrownBy(() -> codec.decode(invalid, "PENDING_REVIEW")).isInstanceOf(ApiException.class);
    }
}
