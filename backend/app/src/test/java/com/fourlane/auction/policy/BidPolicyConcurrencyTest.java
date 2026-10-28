package com.fourlane.auction.policy;

import static org.assertj.core.api.Assertions.*;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class BidPolicyConcurrencyTest extends BidPolicyTestSupport {
    @Test void twoOperatorsCreateDistinctConsecutiveVersionsAndNeverChangeOldRowsInFiveHundredRounds() throws Exception {
        String second = "{\"tiers\":[{\"minPrice\":0,\"increment\":4000},{\"minPrice\":15000,\"increment\":5000}]}";
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 500; round++) {
                int previous = counter();
                var before = policies(); var beforeTiers = tiers();
                var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
                var calls = new ArrayList<Future<HttpResponse<String>>>();
                for (long actor : List.of(firstAdmin, secondAdmin)) calls.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("policy race start timeout");
                    return request("POST", actor == firstAdmin ? VALID : second, actor, "ADMIN");
                }));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
                var first = calls.get(0).get(30, TimeUnit.SECONDS); var other = calls.get(1).get(30, TimeUnit.SECONDS);
                assertThat(List.of(first.statusCode(), other.statusCode())).as("round %s", round).containsExactly(201, 201);
                var a = body(first); var b = body(other);
                assertThat(List.of(a.path("version").asInt(), b.path("version").asInt())).containsExactlyInAnyOrder(previous + 1, previous + 2);
                assertThat(a.path("createdByNickname").asText()).isEqualTo("첫운영자");
                assertThat(b.path("createdByNickname").asText()).isEqualTo("두운영자");
                assertThat(a.at("/tiers/0/increment").asLong()).isEqualTo(2000);
                assertThat(b.at("/tiers/0/increment").asLong()).isEqualTo(4000);
                assertThat(jdbc.queryForObject("SELECT created_by FROM bid_increment_policy WHERE id=?", Long.class, a.path("policyId").asLong())).isEqualTo(firstAdmin);
                assertThat(jdbc.queryForObject("SELECT created_by FROM bid_increment_policy WHERE id=?", Long.class, b.path("policyId").asLong())).isEqualTo(secondAdmin);
                var after = policies(); var afterTiers = tiers();
                assertThat(after).hasSize(before.size() + 2);
                assertThat(after.subList(0, before.size())).isEqualTo(before);
                assertThat(afterTiers).hasSize(beforeTiers.size() + 4);
                assertThat(afterTiers.subList(0, beforeTiers.size())).isEqualTo(beforeTiers);
                assertThat(counter()).isEqualTo(previous + 2);
            }
        }
        var list = body(request("GET", null, firstAdmin, "ADMIN")).path("items");
        assertThat(list.size()).isEqualTo(1001);
        int latestCount = 0;
        for (var policy : list) if (policy.path("isLatest").asBoolean()) latestCount++;
        assertThat(latestCount).isEqualTo(1);
        assertThat(list.get(0).path("version").asInt()).isEqualTo(1001);
    }
}
