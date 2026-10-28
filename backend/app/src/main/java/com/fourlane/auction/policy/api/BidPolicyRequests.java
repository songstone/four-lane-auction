package com.fourlane.auction.policy.api;

import com.fourlane.auction.policy.application.BidPolicyService.InputTier;
import com.fourlane.global.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class BidPolicyRequests {
    private BidPolicyRequests() {}
    @Schema(name = "BidPolicyTierRequest", requiredProperties = {"minPrice", "increment"})
    public record Tier(
            @Schema(implementation = Long.class, minimum = "0", maximum = "9007199254740991") JsonNode minPrice,
            @Schema(implementation = Long.class, minimum = "1", maximum = "9007199254740991", multipleOf = 1000) JsonNode increment) {}
    @Schema(name = "BidPolicyCreateRequest", requiredProperties = {"tiers"})
    public record Create(@ArraySchema(minItems = 1) List<Tier> tiers) {
        public List<InputTier> input() {
            if (tiers == null) return null;
            var result = new java.util.ArrayList<InputTier>();
            for (int i = 0; i < tiers.size(); i++) {
                var tier = tiers.get(i);
                result.add(tier == null ? null : new InputTier(integer(tier.minPrice(), "tiers[" + i + "].minPrice"),
                        integer(tier.increment(), "tiers[" + i + "].increment")));
            }
            return result;
        }
    }
    // Keep raw JSON only in the API layer so Jackson cannot silently coerce strings or decimals to long.
    private static Long integer(JsonNode value, String field) {
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber()) throw ApiException.invalidRequest(field, "INVALID_FORMAT");
        if (!value.canConvertToLong()) throw ApiException.invalidRequest(field, "OUT_OF_RANGE");
        return value.longValue();
    }
}
