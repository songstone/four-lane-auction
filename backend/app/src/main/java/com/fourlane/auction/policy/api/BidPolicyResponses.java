package com.fourlane.auction.policy.api;

import com.fourlane.auction.policy.application.BidPolicyView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class BidPolicyResponses {
    private BidPolicyResponses() {}
    @Schema(name = "BidPolicyTierResponse", requiredProperties = {"minPrice", "increment"})
    public record Tier(long minPrice, long increment) {}
    @Schema(name = "BidPolicyResponse", requiredProperties = {"policyId", "version", "tiers", "createdAt", "createdByNickname", "isLatest"})
    public record Policy(long policyId, int version, List<Tier> tiers, Instant createdAt,
            @Schema(types = {"string", "null"}) String createdByNickname, boolean isLatest) {
        static Policy from(BidPolicyView value) {
            return new Policy(value.policyId(), value.version(), value.tiers().stream()
                    .map(t -> new Tier(t.minPrice(), t.increment())).toList(), value.createdAt(), value.createdByNickname(), value.isLatest());
        }
    }
    @Schema(name = "BidPolicyListResponse", requiredProperties = {"items"})
    public record Policies(List<Policy> items) {}
}
