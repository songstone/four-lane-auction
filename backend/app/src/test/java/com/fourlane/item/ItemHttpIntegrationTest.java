package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fourlane.support.ImageFixtures;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemHttpIntegrationTest extends ItemTestSupport {
    @Test void createsPendingReviewItemWithOrderedImages() throws Exception {
        var response = upload(seller, "USER", Map.of("name", "필름카메라", "description", "셔터 정상", "startPrice", "50000", "reservePrice", "150000"),
                List.of(ImageFixtures.PNG, ImageFixtures.jpeg(), ImageFixtures.WEBP));
        assertThat(response.statusCode()).isEqualTo(201);
        var result = body(response);
        assertThat(result.propertyNames()).containsExactlyInAnyOrder("itemId", "name", "reviewStatus", "createdAt");
        assertThat(result.get("reviewStatus").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(result.get("createdAt").asText()).isEqualTo("2026-10-28T01:02:03.456Z");
        long item = result.get("itemId").asLong();
        assertThat(jdbc.queryForObject("SELECT seller_id FROM item WHERE id=?", Long.class, item)).isEqualTo(seller);
        assertThat(jdbc.queryForObject("SELECT description FROM item WHERE id=?", String.class, item)).isEqualTo("셔터 정상");
        assertThat(jdbc.queryForObject("SELECT reserve_price FROM item WHERE id=?", Long.class, item)).isEqualTo(150000);
        assertThat(jdbc.queryForList("SELECT content_type FROM item_image WHERE item_id=? ORDER BY sort_order", String.class, item))
                .containsExactly("image/png", "image/jpeg", "image/webp");
        assertThat(jdbc.queryForList("SELECT sort_order FROM item_image WHERE item_id=? ORDER BY sort_order", Integer.class, item)).containsExactly(0, 1, 2);
        assertThat(fileCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT created_at FROM item WHERE id=?", Timestamp.class, item).toInstant()).isEqualTo(BASE);
    }
    @Test void defaultsReserveToStartPrice() throws Exception {
        for (var fields : List.of(Map.of("name", "기본값", "startPrice", "50000"), Map.of("name", "빈 값", "startPrice", "50000", "reservePrice", ""))) {
            var response = upload(seller, "USER", fields, List.of(ImageFixtures.PNG));
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(jdbc.queryForObject("SELECT reserve_price = start_price FROM item WHERE id=?", Boolean.class, body(response).get("itemId").asLong())).isTrue();
        }
    }
    @Test void servesDetectedTypeWithNosniffAndRequiresCommittedMetadata() throws Exception {
        var response = upload(seller, "USER", Map.of("name", "PNG", "startPrice", "1000"), List.of(ImageFixtures.PNG));
        assertThat(response.statusCode()).isEqualTo(201);
        String key = jdbc.queryForObject("SELECT storage_key FROM item_image", String.class);
        var image = CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/images/" + key)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body()).isEqualTo(ImageFixtures.PNG);
        assertThat(image.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(image.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(image.headers().firstValue("Cache-Control").orElseThrow()).contains("public", "max-age=31536000", "immutable");
        jdbc.update("DELETE FROM item_image");
        mvc.perform(get("/api/images/" + key)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("IMAGE_NOT_FOUND"));
        assertThat(java.nio.file.Files.exists(ROOT.resolve(key))).isTrue();
    }
    @Test void missingPhysicalFileAndMalformedKeyReturnJson404() throws Exception {
        upload(seller, "USER", Map.of("name", "분실", "startPrice", "1000"), List.of(ImageFixtures.PNG));
        String key = jdbc.queryForObject("SELECT storage_key FROM item_image", String.class);
        java.nio.file.Files.delete(ROOT.resolve(key));
        for (String candidate : List.of(key, "a".repeat(32), "invalid-key"))
            mvc.perform(get("/api/images/" + candidate).accept("text/html")).andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.error.code").value("IMAGE_NOT_FOUND"));
    }
    @Test void isolatesSellersAndPaginatesTiedTimestamps() throws Exception {
        for (int i = 0; i < 3; i++) assertThat(upload(seller, "USER", Map.of("name", "내 상품 " + i, "startPrice", "1000"), List.of(ImageFixtures.PNG)).statusCode()).isEqualTo(201);
        upload(other, "USER", Map.of("name", "남의 상품", "startPrice", "1000"), List.of(ImageFixtures.PNG));
        var first = json.readTree(mvc.perform(get("/api/me/items").param("size", "2").header("Authorization", "Bearer " + token(seller, "USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(first.get("items").size()).isEqualTo(2);
        assertThat(first.at("/items/0/name").asText()).isEqualTo("내 상품 2");
        assertThat(first.at("/items/0/currentLot").isNull()).isTrue();
        assertThat(first.at("/items/0/rejectReason").isNull()).isTrue();
        String cursor = first.get("nextCursor").asText();
        mvc.perform(get("/api/me/items").param("cursor", cursor).header("Authorization", "Bearer " + token(seller, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("내 상품 0"))
                .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/me/items").param("cursor", cursor).header("Authorization", "Bearer " + token(other, "USER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("cursor"));
        mvc.perform(get("/api/me/items").header("Authorization", "Bearer " + token(other, "USER")))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("남의 상품"));
    }
    @Test void reportsReviewStagesAndRejectReasonFromActualRows() throws Exception {
        upload(seller, "USER", Map.of("name", "검수 fixture", "startPrice", "1000"), List.of(ImageFixtures.PNG));
        for (String state : List.of("REJECTED", "APPROVED")) {
            jdbc.update("UPDATE item SET review_status=?,reject_reason=?,reviewed_at=?", state, state.equals("REJECTED") ? "사진 흐림" : null, Timestamp.from(BASE));
            var result = mvc.perform(get("/api/me/items").header("Authorization", "Bearer " + token(seller, "USER")))
                    .andExpect(jsonPath("$.items[0].stage").value(state));
            if (state.equals("REJECTED")) result.andExpect(jsonPath("$.items[0].rejectReason").value("사진 흐림"));
            else result.andExpect(jsonPath("$.items[0].rejectReason").value(org.hamcrest.Matchers.nullValue()));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"12345", "-1000", "1000001000", "999999999999999999999", "1.5", "NaN"})
    void rejectsInvalidMultipartPricesWithoutCreatingFiles(String value) throws Exception {
        var response = upload(seller, "USER", Map.of("name", "잘못된 가격", "startPrice", value), List.of(ImageFixtures.PNG));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body(response).at("/error/code").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(count("item")).isZero(); assertThat(fileCount()).isZero();
    }
    @Test void rejectsUnsupportedSignatureAndTooManyFilesWithoutPersisting() throws Exception {
        var fake = upload(seller, "USER", Map.of("name", "위장", "startPrice", "1000"), List.of("text.jpg".getBytes()));
        assertThat(fake.statusCode()).isEqualTo(400);
        assertThat(body(fake).at("/error/code").asText()).isEqualTo("UNSUPPORTED_IMAGE_TYPE");
        var many = upload(seller, "USER", Map.of("name", "11장", "startPrice", "1000"), Collections.nCopies(11, ImageFixtures.PNG));
        assertThat(many.statusCode()).isEqualTo(400);
        assertThat(body(many).at("/error/fields/0/reason").asText()).isEqualTo("TOO_MANY");
        assertThat(count("item")).isZero(); assertThat(fileCount()).isZero();
    }
    @Test void openApiDescribesMultipartAndRequiredNullableResponseFields() throws Exception {
        var spec = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(spec.at("/paths/~1api~1items/post/requestBody/content/multipart~1form-data").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1items/post/responses/413").isMissingNode()).isFalse();
        assertThat(spec.at("/components/schemas/ItemRegistrationRequest/properties/images/items/format").asText()).isEqualTo("binary");
        assertThat(spec.at("/components/schemas/MyItemSummary/required").toString()).contains("rejectReason", "reviewedAt", "currentLot", "stage");
        assertThat(spec.at("/components/schemas/MyItemSummary/properties/reservePrice").isMissingNode()).isTrue();
        assertThat(spec.at("/components/schemas/MyItemSummary/properties/currentLot/anyOf/1/type").asText()).isEqualTo("null");
    }
}
