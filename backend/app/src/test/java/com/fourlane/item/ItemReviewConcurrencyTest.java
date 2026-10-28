package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemReviewConcurrencyTest extends ItemReviewTestSupport {
    @ParameterizedTest @CsvSource({"approve,reject", "approve,approve", "reject,reject"})
    void exactlyOneCommandWinsInTwoHundredRounds(String first, String second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 200; round++) {
                long id = newItem("경합 " + round);
                var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
                var results = new ArrayList<Future<java.net.http.HttpResponse<String>>>();
                for (String action : List.of(first, second)) results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("review race start timeout");
                    return review(id, action);
                }));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
                var a = results.get(0).get(20, TimeUnit.SECONDS); var b = results.get(1).get(20, TimeUnit.SECONDS);
                assertThat(List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(200, 422);
                var winner = body(a.statusCode() == 200 ? a : b); var loser = body(a.statusCode() == 422 ? a : b);
                String status = winner.path("reviewStatus").asText();
                assertThat(loser.at("/error/code").asText()).isEqualTo("ITEM_NOT_PENDING_REVIEW");
                assertThat(loser.at("/error/reviewStatus").asText()).isEqualTo(status);
                var row = jdbc.queryForMap("SELECT review_status,reject_reason,reviewed_at,updated_at FROM item WHERE id=?", id);
                assertThat(row.get("review_status")).isEqualTo(status);
                assertThat(row.get("reject_reason")).isEqualTo(status.equals("REJECTED") ? "사진 확인 불가" : null);
                assertThat(row.get("reviewed_at")).isEqualTo(row.get("updated_at"));
                assertThat(winner.path("reviewedAt").asText()).isEqualTo(BASE.toString());
            }
        }
    }
}
