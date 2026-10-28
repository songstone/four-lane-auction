package com.fourlane.auction.session;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fourlane.support.contract.ReservePriceAssertions;

class PublicSessionHttpIntegrationTest extends SessionLineupTestSupport {
    @ParameterizedTest @ValueSource(ints={1,2,3,4})
    void allLanesAndReadySnapshotsUseOrderedRealLineup(int laneCount) throws Exception {
        var created = request("POST", "/api/admin/sessions", VALID.replace("\"laneCount\":2", "\"laneCount\":"+laneCount));
        long id = body(created).at("/session/sessionId").asLong();
        long a=item("첫 물품",true), b=item("둘째 물품",true);
        assertThat(request("PUT",path(id,1),ids(b,a)).statusCode()).isEqualTo(200);
        var response=request("GET","/api/sessions/"+id,null,null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var doc=body(response);
        assertThat(doc.path("serverTime").asText()).isEqualTo(BASE.toString());
        assertThat(doc.path("me").isNull()).isTrue();
        assertThat(doc.path("lanes").size()).isEqualTo(laneCount);
        String epoch=doc.at("/lanes/0/snapshot/epoch").asText();
        for(int n=0;n<laneCount;n++) {
            var lane=doc.path("lanes").get(n);
            assertThat(lane.path("laneNo").asInt()).isEqualTo(n+1);
            assertThat(lane.at("/snapshot/epoch").asText()).isEqualTo(epoch).isNotBlank();
            assertThat(lane.at("/snapshot/seq").asInt()).isZero();
            assertThat(lane.at("/snapshot/serverTime").asText()).isEqualTo(doc.path("serverTime").asText());
            assertThat(lane.at("/snapshot/heartbeatIntervalMs").asInt()).isEqualTo(5000);
            assertThat(lane.at("/snapshot/me").isNull()).isTrue();
            assertThat(lane.at("/snapshot/nextLotInfo").isNull()).isTrue();
            assertThat(lane.at("/snapshot/state/laneStatus").asText()).isEqualTo("READY");
            if(n>0) {
                assertThat(lane.path("lineup").isEmpty()).isTrue();
                assertThat(lane.at("/snapshot/state/lot").isNull()).isTrue();
                assertThat(lane.at("/snapshot/state/position").asInt()).isZero();
                assertThat(lane.at("/snapshot/state/remainingLots").asInt()).isZero();
            }
        }
        assertThat(doc.at("/lanes/0/lineup/0/name").asText()).isEqualTo("둘째 물품");
        assertThat(doc.at("/lanes/0/lineup/1/seqNo").asInt()).isEqualTo(2);
        assertThat(doc.at("/lanes/0/snapshot/state/position").asInt()).isEqualTo(1);
        assertThat(doc.at("/lanes/0/snapshot/state/remainingLots").asInt()).isEqualTo(1);
        assertThat(doc.at("/lanes/0/snapshot/state/lot/status").asText()).isEqualTo("PENDING");
        assertThat(doc.at("/lanes/0/snapshot/state/lot/currentPrice").asLong()).isEqualTo(50000);
        assertThat(doc.at("/lanes/0/snapshot/state/lot/reserveNotMet").asBoolean()).isFalse();
        assertThat(doc.at("/lanes/0/snapshot/state/lot/nextPrice").isNull()).isTrue();
        ReservePriceAssertions.assertHidden(doc,Set.of(917431000L));
    }
    @Test void personalLotsAndReplacementRemainConsistentAndOldLotDisappears() throws Exception {
        long id=create(), a=item("내 물품",true);
        request("PUT",path(id,1),ids(a));
        var before=body(request("GET","/api/sessions/"+id,null,"USER"));
        long old=before.at("/lanes/0/lineup/0/lotId").asLong();
        assertThat(before.at("/me/myLotIds/0").asLong()).isEqualTo(old);
        assertThat(before.at("/lanes/0/snapshot/me/isHighestBidder").asBoolean()).isFalse();
        var adminView=body(request("GET","/api/sessions/"+id,null));
        assertThat(adminView.at("/me/myLotIds").isEmpty()).isTrue();
        request("PUT",path(id,1),ids(a));
        var after=body(request("GET","/api/sessions/"+id,null,"USER"));
        long current=after.at("/lanes/0/lineup/0/lotId").asLong();
        assertThat(current).isNotEqualTo(old);
        assertThat(after.at("/me/myLotIds/0").asLong()).isEqualTo(current);
        assertThat(after.at("/lanes/0/snapshot/state/lot/lotId").asLong()).isEqualTo(current);
        var gone=request("GET","/api/lots/"+old,null,null);
        assertThat(gone.statusCode()).isEqualTo(404);
        assertThat(body(gone).at("/error/code").asText()).isEqualTo("LOT_NOT_FOUND");
    }
    @Test void lotDetailHasImagesInOrderAndConditionalFinalPrice() throws Exception {
        long id=create(), a=item("상세 물품",true);
        request("PUT",path(id,1),ids(a));
        long lot=body(request("GET","/api/sessions/"+id,null,null)).at("/lanes/0/lineup/0/lotId").asLong();
        var response=request("GET","/api/lots/"+lot,null,null);
        assertThat(response.statusCode()).isEqualTo(200);
        var doc=body(response);
        assertThat(doc.path("name").asText()).isEqualTo("상세 물품");
        assertThat(doc.path("sessionId").asLong()).isEqualTo(id);
        assertThat(doc.path("images").size()).isEqualTo(1);
        assertThat(doc.at("/images/0/sortOrder").asInt()).isZero();
        assertThat(doc.at("/images/0/url").asText()).startsWith("/api/images/");
        assertThat(doc.path("finalPrice").isNull()).isTrue();
        assertThat(doc.path("closedAt").isNull()).isTrue();
        ReservePriceAssertions.assertHidden(doc,Set.of(917431000L));
        // Query mapping fixtures, not evidence of the future auction engine.
        for(String status:List.of("SOLD","UNSOLD")) {
            jdbc.update("UPDATE lot SET status=?,current_price=60000,bid_count=1,last_bid_id=123,bidding_started_at=?,highest_bidder_id=?,closed_at=?,unsold_reason=? WHERE id=?",
                status,java.sql.Timestamp.from(BASE),other,java.sql.Timestamp.from(BASE),status.equals("UNSOLD")?"RESERVE_NOT_MET":null,lot);
            assertThat(body(request("GET","/api/lots/"+lot,null,null)).path("finalPrice").asLong()).isEqualTo(60000);
        }
        jdbc.update("UPDATE lot SET bid_count=0,last_bid_id=null,highest_bidder_id=null,unsold_reason='NO_BID' WHERE id=?",lot);
        assertThat(body(request("GET","/api/lots/"+lot,null,null)).path("finalPrice").isNull()).isTrue();
    }
    @Test void missingAndUnsupportedSessionHaveDifferentErrors() throws Exception {
        var missing=request("GET","/api/sessions/9000000000",null,null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(body(missing).at("/error/code").asText()).isEqualTo("SESSION_NOT_FOUND");
        long id=create();
        for(String status:List.of("RUNNING","FINISHED")) {
            jdbc.update("UPDATE auction_session SET status=?,bid_policy_id=1 WHERE id=?",status,id);
            var response=request("GET","/api/sessions/"+id,null,null);
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(body(response).at("/error/code").asText()).isEqualTo("INTERNAL_ERROR");
        }
    }
    @Test void openApiHasRequiredNullableObjectsAndJsonErrors() throws Exception {
        var doc=body(request("GET","/v3/api-docs",null,null));
        var schemas=doc.at("/components/schemas");
        assertThat(schemas.at("/PublicSessionResponse/properties/me/anyOf").size()).isEqualTo(2);
        assertThat(schemas.at("/PublicLaneState/properties/lot/anyOf").size()).isEqualTo(2);
        assertThat(schemas.at("/PublicLaneSnapshot/properties/nextLotInfo/anyOf").size()).isEqualTo(2);
        assertThat(doc.at("/paths/~1api~1sessions~1{sessionId}/get/responses/200/content/application~1json").isMissingNode()).isFalse();
        assertThat(doc.at("/paths/~1api~1lots~1{lotId}/get/responses/404/content/application~1json").isMissingNode()).isFalse();
        assertThat(schemas.at("/PublicLotState/required").toString()).contains("reserveNotMet","nextPrice");
    }
}
