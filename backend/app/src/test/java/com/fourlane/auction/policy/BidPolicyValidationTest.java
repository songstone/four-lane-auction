package com.fourlane.auction.policy;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.auction.policy.application.BidPolicyService;
import com.fourlane.auction.policy.application.BidPolicyService.InputTier;
import com.fourlane.global.error.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;

class BidPolicyValidationTest {
    @Test void allowsSafeIntegerBoundaryAndUnroundedMinPrice() {
        BidPolicyService.validate(List.of(new InputTier(0L, 9_007_199_254_740_000L), new InputTier(BidPolicyService.MAX_SAFE_INTEGER, 1000L)));
        BidPolicyService.validate(List.of(new InputTier(0L, 1000L), new InputTier(1L, 2000L)));
    }
    @Test void reportsTheFirstExactFieldWithoutSorting() {
        invalid(null, "tiers", "REQUIRED");
        invalid(List.of(), "tiers", "REQUIRED");
        invalid(Arrays.asList((InputTier) null), "tiers[0]", "REQUIRED");
        invalid(List.of(new InputTier(null, 1000L)), "tiers[0].minPrice", "REQUIRED");
        invalid(List.of(new InputTier(0L, null)), "tiers[0].increment", "REQUIRED");
        invalid(List.of(new InputTier(1L, 1000L)), "tiers[0].minPrice", "OUT_OF_RANGE");
        invalid(List.of(new InputTier(-1L, 1000L)), "tiers[0].minPrice", "OUT_OF_RANGE");
        invalid(List.of(new InputTier(0L, 0L)), "tiers[0].increment", "OUT_OF_RANGE");
        invalid(List.of(new InputTier(0L, 1001L)), "tiers[0].increment", "NOT_MULTIPLE_OF_1000");
        invalid(List.of(new InputTier(0L, BidPolicyService.MAX_SAFE_INTEGER + 1)), "tiers[0].increment", "OUT_OF_RANGE");
        invalid(List.of(new InputTier(0L, 1000L), new InputTier(0L, 2000L)), "tiers[1].minPrice", "DUPLICATE");
        invalid(List.of(new InputTier(0L, 1000L), new InputTier(10000L, 2000L), new InputTier(9999L, 3000L)), "tiers[2].minPrice", "INVALID_FORMAT");
    }
    private static void invalid(List<InputTier> tiers, String field, String reason) {
        assertThatThrownBy(() -> BidPolicyService.validate(tiers)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getFields()).hasSize(1);
            assertThat(e.getFields().getFirst().field()).isEqualTo(field);
            assertThat(e.getFields().getFirst().reason()).isEqualTo(reason);
        });
    }
}
