package com.fourlane.auction.policy;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.auction.policy.application.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BidPolicyHttpIntegrationTest extends BidPolicyTestSupport {
    @Autowired BidPolicyService service;
    @Test void mapsTheSeedAndListsImmutableVersionsWithExactlyOneLatest() throws Exception {
        var before = policies(); var oldTiers = tiers();
        var seed = service.list().getFirst();
        assertThat(seed.createdByNickname()).isNull();
        assertThat(BidPolicyMapper.toEngine(seed).nextPrice(50000)).isEqualTo(60000);
        long[] prices = {9999,10000,99999,100000}, expected = {10999,20000,109999,125000};
        for (int i = 0; i < prices.length; i++) assertThat(BidPolicyMapper.toEngine(seed).nextPrice(prices[i])).isEqualTo(expected[i]);
        for (int version = 2; version <= 3; version++) {
            var response = request("POST", VALID, firstAdmin, "ADMIN");
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(body(response).path("version").asInt()).isEqualTo(version);
            assertThat(body(response).path("createdByNickname").asText()).isEqualTo("첫운영자");
            assertThat(body(response).path("createdAt").asText()).isEqualTo("2026-10-28T03:04:05.000Z");
        }
        assertThat(policies().subList(0, 1)).isEqualTo(before);
        assertThat(tiers().subList(0, 3)).isEqualTo(oldTiers);
        var list = body(request("GET", null, firstAdmin, "ADMIN")).path("items");
        assertThat(list.size()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            assertThat(list.get(i).path("version").asInt()).isEqualTo(3 - i);
            assertThat(list.get(i).path("isLatest").asBoolean()).isEqualTo(i == 0);
        }
        assertThat(list.get(2).has("createdByNickname")).isTrue();
        assertThat(list.get(2).get("createdByNickname").isNull()).isTrue();
        assertThat(list.get(0).at("/tiers/1/minPrice").asLong()).isEqualTo(9999);
    }
    @Test void rejectsInvalidShapesTypesRangesAndOrderingWithoutAllocatingAVersion() throws Exception {
        String[][] cases = {
            {"{}", "tiers", "REQUIRED"}, {"{\"tiers\":null}", "tiers", "REQUIRED"}, {"{\"tiers\":[]}", "tiers", "REQUIRED"},
            {"{\"tiers\":[null]}", "tiers[0]", "REQUIRED"}, {"{\"tiers\":[{}]}", "tiers[0].minPrice", "REQUIRED"},
            {"{\"tiers\":[{\"minPrice\":0}]}", "tiers[0].increment", "REQUIRED"},
            {"{\"tiers\":[{\"minPrice\":1,\"increment\":1000}]}", "tiers[0].minPrice", "OUT_OF_RANGE"},
            {"{\"tiers\":[{\"minPrice\":-1,\"increment\":1000}]}", "tiers[0].minPrice", "OUT_OF_RANGE"},
            {"{\"tiers\":[{\"minPrice\":0,\"increment\":0}]}", "tiers[0].increment", "OUT_OF_RANGE"},
            {"{\"tiers\":[{\"minPrice\":0,\"increment\":1001}]}", "tiers[0].increment", "NOT_MULTIPLE_OF_1000"},
            {"{\"tiers\":[{\"minPrice\":0,\"increment\":\"1000\"}]}", "tiers[0].increment", "INVALID_FORMAT"},
            {"{\"tiers\":[{\"minPrice\":0,\"increment\":1000.0}]}", "tiers[0].increment", "INVALID_FORMAT"},
            {"{\"tiers\":[{\"minPrice\":false,\"increment\":1000}]}", "tiers[0].minPrice", "INVALID_FORMAT"},
            {"{\"tiers\":[{\"minPrice\":0,\"increment\":9007199254740992}]}", "tiers[0].increment", "OUT_OF_RANGE"},
            {"{\"tiers\":[{\"minPrice\":0,\"increment\":9223372036854775808}]}", "tiers[0].increment", "OUT_OF_RANGE"},
            {"{\"tiers\":[{\"minPrice\":0,\"increment\":1000},{\"minPrice\":9007199254740992,\"increment\":1000}]}", "tiers[1].minPrice", "OUT_OF_RANGE"},
            {"{\"tiers\":[{\"minPrice\":0,\"increment\":1000},{\"minPrice\":0,\"increment\":2000}]}", "tiers[1].minPrice", "DUPLICATE"},
            {"{\"tiers\":[{\"minPrice\":0,\"increment\":1000},{\"minPrice\":10000,\"increment\":2000},{\"minPrice\":9999,\"increment\":3000}]}", "tiers[2].minPrice", "INVALID_FORMAT"}
        };
        var before = policies(); var beforeTiers = tiers();
        for (var c : cases) {
            var response = request("POST", c[0], firstAdmin, "ADMIN");
            assertThat(response.statusCode()).as(c[0]).isEqualTo(400);
            assertThat(body(response).at("/error/code").asText()).isEqualTo("INVALID_REQUEST");
            assertThat(body(response).at("/error/fields/0/field").asText()).as(c[0]).isEqualTo(c[1]);
            assertThat(body(response).at("/error/fields/0/reason").asText()).isEqualTo(c[2]);
        }
        var broken = request("POST", "{", firstAdmin, "ADMIN");
        assertThat(broken.statusCode()).isEqualTo(400);
        assertThat(body(broken).at("/error/fields").isEmpty()).isTrue();
        assertThat(counter()).isEqualTo(1); assertThat(policies()).isEqualTo(before); assertThat(tiers()).isEqualTo(beforeTiers);
    }
    @Test void roundTripsTheSafeIntegerLimitsAndIgnoresUnknownProperties() throws Exception {
        var response = request("POST", "{\"version\":999,\"tiers\":[{\"minPrice\":0,\"increment\":9007199254740000},{\"minPrice\":9007199254740991,\"increment\":1000}]}", firstAdmin, "ADMIN");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(body(response).path("version").asInt()).isEqualTo(2);
        assertThat(body(response).at("/tiers/0/increment").asLong()).isEqualTo(9_007_199_254_740_000L);
        assertThat(body(response).at("/tiers/1/minPrice").asLong()).isEqualTo(BidPolicyService.MAX_SAFE_INTEGER);
        assertThat(body(request("GET", null, firstAdmin, "ADMIN")).at("/items/0/tiers/1/minPrice").asLong()).isEqualTo(BidPolicyService.MAX_SAFE_INTEGER);
    }
    @Test void bothEndpointsEnforceAuthorizationWithoutWrites() throws Exception {
        for (String method : List.of("GET", "POST")) {
            assertThat(request(method, method.equals("POST") ? VALID : null, user, "USER").statusCode()).isEqualTo(403);
            assertThat(request(method, method.equals("POST") ? VALID : null, 0, null).statusCode()).isEqualTo(401);
        }
        assertThat(counter()).isEqualTo(1); assertThat(policies()).hasSize(1); assertThat(tiers()).hasSize(3);
    }
    @Test void openApiDescribesIntegerInputsRequiredNullsAndAllResponses() throws Exception {
        var spec = body(path("GET", "/v3/api-docs", null, 0, null));
        String base = "/paths/~1api~1admin~1bid-policies";
        assertThat(spec.at(base + "/get/responses/200/content/application~1json").isMissingNode()).isFalse();
        assertThat(spec.at(base + "/post/responses/201/content/application~1json").isMissingNode()).isFalse();
        for (String method : List.of("get", "post")) for (String status : List.of("400", "401", "403", "500"))
            assertThat(spec.at(base + "/" + method + "/responses/" + status + "/content/application~1json").isMissingNode()).isFalse();
        var schema = spec.at("/components/schemas/BidPolicyTierRequest/properties");
        assertThat(schema.at("/minPrice/type").asText()).isEqualTo("integer");
        assertThat(schema.at("/increment/type").asText()).isEqualTo("integer");
        assertThat(schema.at("/minPrice/maximum").asLong()).isEqualTo(BidPolicyService.MAX_SAFE_INTEGER);
        assertThat(spec.at("/components/schemas/BidPolicyCreateRequest/properties/tiers/minItems").asInt()).isEqualTo(1);
        assertThat(spec.at("/components/schemas/BidPolicyResponse/required").toString()).contains("createdByNickname", "isLatest", "createdAt", "policyId", "tiers", "version");
        assertThat(spec.at("/components/schemas/BidPolicyResponse/properties/createdByNickname/type").toString()).contains("null", "string");
    }
}
