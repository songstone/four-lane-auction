package com.fourlane.auction.session;

import static org.assertj.core.api.Assertions.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;

class SessionLineupHttpIntegrationTest extends SessionLineupTestSupport {
    @Test void replacesInOrderCopiesPricesAllocatesFreshIdsAndUpdatesSellerStage() throws Exception {
        long session=create(), a=item("첫 상품",true), b=item("둘째 상품",true), c=item("셋째 상품",true);
        var r=request("PUT",path(session,1),ids(a,b,c)); assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        var rows=body(r).path("lots"); long first=rows.get(0).path("lotId").asLong();
        assertThat(rows.size()).isEqualTo(3);
        for(int i=0;i<3;i++) { assertThat(rows.get(i).path("seqNo").asInt()).isEqualTo(i+1); assertThat(rows.get(i).path("status").asText()).isEqualTo("PENDING"); }
        assertThat(rows.get(0).path("itemId").asLong()).isEqualTo(a);
        for(var row:jdbc.queryForList("SELECT * FROM lot")) {
            assertThat(((Number)row.get("start_price")).longValue()).isEqualTo(50000);
            assertThat(((Number)row.get("reserve_price")).longValue()).isEqualTo(917431000);
            assertThat(row.get("current_price")).isEqualTo(row.get("start_price")); assertThat(row.get("bid_count")).isEqualTo(0);
            assertThat(row.get("created_at")).isEqualTo(row.get("updated_at")); assertThat(row.get("deadline_at")).isNull();
        }
        assertThat(r.body()).doesNotContain("reservePrice","917431000");
        var d=detail(session); assertThat(d.path("startable").asBoolean()).isFalse(); assertThat(d.path("emptyLaneNos").toString()).isEqualTo("[2]");
        assertThat(request("PUT",path(session,1),ids(c,a,b)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lot WHERE id=?",Integer.class,first)).isZero();
        assertThat(body(request("GET","/api/admin/items?stage=APPROVED",null)).path("items").isEmpty()).isTrue();
        var mine=body(request("GET","/api/me/items",null,"USER")).path("items");
        assertThat(mine.get(0).path("stage").asText()).isEqualTo("SCHEDULED");
        assertThat(mine.get(0).at("/currentLot/sessionId").asLong()).isEqualTo(session);
        assertThat(mine.get(0).at("/currentLot/seqNo").asInt()).isEqualTo(1);
        long extra=item("다른 레인",true); lineup.replace(session,2,List.of(extra));
        assertThat(detail(session).path("startable").asBoolean()).isTrue();
        assertThat(request("PUT",path(session,1),ids()).statusCode()).isEqualTo(200);
        assertThat(body(request("GET","/api/admin/items?stage=APPROVED",null)).path("items").size()).isEqualTo(3);
        var last=body(request("GET","/api/me/items",null,"USER")).path("items");
        for(var item:last) if(item.path("itemId").asLong()!=extra) { assertThat(item.path("stage").asText()).isEqualTo("APPROVED"); assertThat(item.path("currentLot").isNull()).isTrue(); }
    }
    @Test void reportsItemErrorsAndKeepsOldLineupUnchanged() throws Exception {
        long session=create(), a=item("승인",true), pending=item("대기",false), rejected=item("반려",false);
        request("POST","/api/admin/items/"+rejected+"/reject","{\"reason\":\"사진 보완\"}");
        lineup.replace(session,1,List.of(a));
        var missing=request("PUT",path(session,1),ids(a,9000000000L));
        assertThat(missing.statusCode()).isEqualTo(404); assertThat(body(missing).at("/error/itemIds/0").asLong()).isEqualTo(9000000000L);
        var denied=request("PUT",path(session,1),ids(pending,rejected));
        assertThat(denied.statusCode()).isEqualTo(422); assertThat(body(denied).at("/error/items/0/stage").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(body(denied).at("/error/items/1/stage").asText()).isEqualTo("REJECTED");
        var duplicate=request("PUT",path(session,1),ids(a,a)); assertThat(duplicate.statusCode()).isEqualTo(400);
        assertThat(body(duplicate).at("/error/fields/0/reason").asText()).isEqualTo("DUPLICATE");
        var elsewhere=request("PUT",path(session,2),ids(a)); assertThat(elsewhere.statusCode()).isEqualTo(422);
        assertThat(body(elsewhere).at("/error/items/0/stage").asText()).isEqualTo("SCHEDULED");
        long otherSession=create(); assertThat(request("PUT",path(otherSession,1),ids(a)).statusCode()).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lot WHERE item_id=?",Integer.class,a)).isEqualTo(1);
        assertThat(request("PUT",path(session,3),ids()).statusCode()).isEqualTo(404);
        assertThat(request("PUT",path(9000000000L,1),ids()).statusCode()).isEqualTo(404);
        assertThat(request("PUT",path(session,1),ids(),"USER").statusCode()).isEqualTo(403);
        assertThat(request("PUT",path(session,1),ids(),null).statusCode()).isEqualTo(401);
    }
    @Test void rejectsAuctionAndResultStagesAndStartedSessionsFromActualRows() throws Exception {
        long session=create(), a=item("경매 이력",true); lineup.replace(session,1,List.of(a));
        for(String status:List.of("BIDDING","SOLD","UNSOLD")) {
            jdbc.update("UPDATE lot SET status=?,bidding_started_at=?,closed_at=?,highest_bidder_id=?,unsold_reason=? WHERE item_id=?",status,
                Timestamp.from(BASE),status.equals("BIDDING")?null:Timestamp.from(BASE),status.equals("SOLD")?other:null,status.equals("UNSOLD")?"NO_BID":null,a);
            var r=request("PUT",path(session,1),ids(a)); assertThat(r.statusCode()).isEqualTo(422);
            assertThat(body(r).at("/error/items/0/stage").asText()).isEqualTo(status.equals("BIDDING")?"ON_AUCTION":status);
        }
        jdbc.update("UPDATE auction_session SET status='RUNNING',bid_policy_id=1 WHERE id=?",session);
        var r=request("PUT",path(session,1),ids()); assertThat(r.statusCode()).isEqualTo(422);
        assertThat(body(r).at("/error/code").asText()).isEqualTo("SESSION_NOT_SCHEDULED");
        var spec=body(request("GET","/v3/api-docs",null,null));
        assertThat(spec.at("/components/schemas/SessionLineupRequest/properties/itemIds/type").asText()).isEqualTo("array");
        assertThat(spec.at("/components/schemas/ErrorDetail/properties/items").isMissingNode()).isFalse();
    }
}
