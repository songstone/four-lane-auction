package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.support.ImageFixtures;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ItemRegistrationConcurrencyTest extends ItemTestSupport {
    @Test void isolatesFilesAndOwnersAcrossTwoHundredSimultaneousRegistrationRounds() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 200; round++) {
                var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
                var results = new ArrayList<Future<java.net.http.HttpResponse<String>>>();
                for (long owner : new long[]{seller, other}) results.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("registration start timeout");
                    return upload(owner, "USER", Map.of("name", "같은 파일명", "startPrice", "1000"), List.of(ImageFixtures.PNG));
                }));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
                for (var result : results) assertThat(result.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
            }
        }
        assertThat(count("item")).isEqualTo(400); assertThat(count("item_image")).isEqualTo(400); assertThat(fileCount()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT storage_key) FROM item_image", Integer.class)).isEqualTo(400);
        for (long owner : new long[]{seller, other}) assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item WHERE seller_id=?", Integer.class, owner)).isEqualTo(200);
        for (String key : jdbc.queryForList("SELECT storage_key FROM item_image", String.class))
            assertThat(java.nio.file.Files.readAllBytes(ROOT.resolve(key))).isEqualTo(ImageFixtures.PNG);
    }
}
