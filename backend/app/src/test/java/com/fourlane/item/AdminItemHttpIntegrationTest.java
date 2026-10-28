package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;

class AdminItemHttpIntegrationTest extends ItemReviewTestSupport {
    @Test void approvalMakesItemAssignableAndRejectionRemainsVisibleToSeller() throws Exception {
        long approved = newItem("승인할 상품"), rejected = newItem("반려할 상품");
        assertThat(review(approved, "approve").statusCode()).isEqualTo(200);
        String reason = "  사진이 흐립니다 😀  ";
        var rejection = request("POST", "/api/admin/items/" + rejected + "/reject", admin, "ADMIN", json.writeValueAsString(Map.of("reason", reason)));
        assertThat(rejection.statusCode()).isEqualTo(200);
        var candidates = list("APPROVED"); assertThat(candidates.statusCode()).isEqualTo(200);
        assertThat(body(candidates).at("/items/0/itemId").asLong()).isEqualTo(approved);
        assertThat(body(candidates).path("items").size()).isEqualTo(1);
        var sellerItems = body(request("GET", "/api/me/items", seller, "USER", null)).path("items");
        assertThat(sellerItems.get(0).path("stage").asText()).isEqualTo("REJECTED");
        assertThat(sellerItems.get(1).path("stage").asText()).isEqualTo("APPROVED");
        assertThat(sellerItems.get(0).path("rejectReason").asText()).isEqualTo(reason);
        assertThat(sellerItems.get(0).path("reviewedAt").asText()).isEqualTo("2026-10-28T01:02:03.456Z");
        assertThat(body(list("PENDING_REVIEW")).path("items").isEmpty()).isTrue();
        for (long id : new long[]{approved, rejected}) {
            for (String command : List.of("approve", "reject")) {
                var repeated = review(id, command);
                assertThat(repeated.statusCode()).isEqualTo(422);
                assertThat(body(repeated).at("/error/reviewStatus").asText()).isEqualTo(id == approved ? "APPROVED" : "REJECTED");
            }
            var row = jdbc.queryForMap("SELECT reviewed_at,updated_at,reject_reason FROM item WHERE id=?", id);
            assertThat(jdbc.queryForObject("SELECT reviewed_at FROM item WHERE id=?", Timestamp.class, id).toInstant()).isEqualTo(BASE);
            assertThat(row.get("updated_at")).isEqualTo(row.get("reviewed_at"));
            assertThat(row.get("reject_reason")).isEqualTo(id == rejected ? reason : null);
        }
    }
    @Test void server403ProtectsAllFourEndpointsAndNeverMutatesTheItem() throws Exception {
        long id = newItem("권한 검사");
        for (String path : List.of("/api/admin/items?stage=PENDING_REVIEW", "/api/admin/items/" + id)) {
            assertThat(request("GET", path, seller, "USER", null).statusCode()).isEqualTo(403);
            assertThat(request("GET", path, 0, null, null).statusCode()).isEqualTo(401);
        }
        for (String action : List.of("approve", "reject")) {
            var response = request("POST", "/api/admin/items/" + id + "/" + action, seller, "USER", "{\"reason\":\"확인 불가\"}");
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(body(response).at("/error/code").asText()).isEqualTo("FORBIDDEN");
        }
        assertThat(jdbc.queryForObject("SELECT review_status FROM item WHERE id=?", String.class, id)).isEqualTo("PENDING_REVIEW");
    }
    @Test void validatesReasonsMissingItemsAndQueryParameters() throws Exception {
        long id = newItem("입력 검사");
        for (String content : List.of("{}", "{\"reason\":null}", "{\"reason\":\"  \"}")) {
            var result = request("POST", "/api/admin/items/" + id + "/reject", admin, "ADMIN", content);
            assertThat(result.statusCode()).isEqualTo(400);
        }
        var tooLong = request("POST", "/api/admin/items/" + id + "/reject", admin, "ADMIN", json.writeValueAsString(Map.of("reason", "😀".repeat(501))));
        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(body(tooLong).at("/error/fields/0/reason").asText()).isEqualTo("TOO_LONG");
        var accepted = request("POST", "/api/admin/items/" + id + "/reject", admin, "ADMIN", json.writeValueAsString(Map.of("reason", "😀".repeat(500))));
        assertThat(accepted.statusCode()).isEqualTo(200);
        for (String action : List.of("approve", "reject")) assertThat(review(9_000_000_000L, action).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/api/admin/items/9000000000", admin, "ADMIN", null).statusCode()).isEqualTo(404);
        for (String suffix : List.of("", "?stage=WRONG", "?stage=PENDING_REVIEW&size=0", "?stage=PENDING_REVIEW&size=101", "?stage=PENDING_REVIEW&cursor=wrong"))
            assertThat(request("GET", "/api/admin/items" + suffix, admin, "ADMIN", null).statusCode()).isEqualTo(400);
    }
    @Test void paginatesByStageAndIdAndOnlyDetailIncludesReserve() throws Exception {
        long first = newItem("첫 번째"), second = newItem("두 번째"), third = newItem("세 번째");
        var firstPage = body(request("GET", "/api/admin/items?stage=PENDING_REVIEW&size=2", admin, "ADMIN", null));
        assertThat(firstPage.at("/items/0/itemId").asLong()).isEqualTo(first);
        assertThat(firstPage.at("/items/1/itemId").asLong()).isEqualTo(second);
        assertThat(firstPage.toString()).doesNotContain("reservePrice", "917431000");
        String cursor = firstPage.path("nextCursor").asText();
        var last = body(request("GET", "/api/admin/items?stage=PENDING_REVIEW&cursor=" + cursor, admin, "ADMIN", null));
        assertThat(last.path("items").size()).isEqualTo(1); assertThat(last.at("/items/0/itemId").asLong()).isEqualTo(third);
        assertThat(last.path("nextCursor").isNull()).isTrue();
        assertThat(request("GET", "/api/admin/items?stage=APPROVED&cursor=" + cursor, admin, "ADMIN", null).statusCode()).isEqualTo(400);
        review(first, "approve"); review(second, "approve");
        var approved = body(list("APPROVED"));
        assertThat(approved.at("/items/0/itemId").asLong()).isEqualTo(second);
        var detail = body(request("GET", "/api/admin/items/" + first, admin, "ADMIN", null));
        assertThat(detail.path("reservePrice").asLong()).isEqualTo(917431000);
        assertThat(detail.path("images").size()).isEqualTo(1); assertThat(detail.at("/images/0/sortOrder").asInt()).isZero();
        assertThat(detail.path("lots").isEmpty()).isTrue();
        var spec = body(request("GET", "/v3/api-docs", 0, null, null));
        assertThat(spec.at("/components/schemas/AdminItemDetail/required").toString()).contains("reservePrice", "reviewedAt", "description", "rejectReason");
        assertThat(spec.at("/components/schemas/AdminItemSummary/properties/reservePrice").isMissingNode()).isTrue();
        assertThat(spec.at("/components/schemas/ErrorDetail/properties/reviewStatus").isMissingNode()).isFalse();
    }
    @Test void derivesAllStagesFromLatestLotAfterReviewAndReturnsActualHistory() throws Exception {
        long id = newItem("이력 검사"); review(id, "approve");
        jdbc.update("INSERT INTO auction_session(status,lane_count,scheduled_start_at,first_bid_wait_ms,soft_close_ms,lot_gap_ms,resume_min_ms,created_at,updated_at) VALUES('SCHEDULED',1,?,15000,10000,5000,10000,?,?)", Timestamp.from(BASE), Timestamp.from(BASE), Timestamp.from(BASE));
        long session = jdbc.queryForObject("SELECT id FROM auction_session", Long.class);
        jdbc.update("INSERT INTO lane(session_id,lane_no,status,created_at,updated_at) VALUES(?,1,'READY',?,?)", session, Timestamp.from(BASE), Timestamp.from(BASE));
        long lane = jdbc.queryForObject("SELECT id FROM lane", Long.class);
        int seq = 0;
        for (String status : List.of("UNSOLD", "PENDING", "BIDDING", "SOLD", "UNSOLD")) {
            boolean closed = status.equals("SOLD") || status.equals("UNSOLD");
            ++seq;
            // Equal timestamps exercise reviewedAt inclusivity and newest-id tie breaking.
            var at = Timestamp.from(BASE);
            jdbc.update("""
                INSERT INTO lot(lane_id,item_id,seq_no,status,start_price,reserve_price,current_price,highest_bidder_id,bid_count,last_bid_id,bidding_started_at,closed_at,unsold_reason,created_at,updated_at)
                VALUES(?,?,?,?,50000,917431000,60000,?,1,1,?,?,?,?,?)
                """, lane, id, seq, status, other, at, closed ? at : null, status.equals("UNSOLD") ? "RESERVE_NOT_MET" : null, at, at);
            String stage = status.equals("PENDING") ? "SCHEDULED" : status.equals("BIDDING") ? "ON_AUCTION" : status;
            var matching = body(list(stage)); assertThat(matching.path("items").size()).isEqualTo(1);
            assertThat(body(list("APPROVED")).path("items").isEmpty()).isTrue();
            var detail = body(request("GET", "/api/admin/items/" + id, admin, "ADMIN", null));
            assertThat(detail.path("stage").asText()).isEqualTo(stage);
            assertThat(detail.path("lots").size()).isEqualTo(seq);
            assertThat(detail.at("/lots/0/status").asText()).isEqualTo(status);
            if (closed) assertThat(detail.at("/lots/0/finalPrice").asLong()).isEqualTo(60000);
            else assertThat(detail.at("/lots/0/finalPrice").isNull()).isTrue();
        }
        jdbc.update("UPDATE item SET reviewed_at=? WHERE id=?", Timestamp.from(BASE.plusSeconds(6)), id);
        assertThat(body(list("APPROVED")).path("items").size()).isEqualTo(1);
        // The old Lot history remains; the new review window alone controls assignability.
        assertThat(body(request("GET", "/api/admin/items/" + id, admin, "ADMIN", null)).path("lots").size()).isEqualTo(5);
    }
}
