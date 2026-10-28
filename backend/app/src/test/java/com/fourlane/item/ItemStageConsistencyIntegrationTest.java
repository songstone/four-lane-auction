package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.auction.session.SessionLineupTestSupport;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;

class ItemStageConsistencyIntegrationTest extends SessionLineupTestSupport {
    @Test void sellerAdminFilterAndDetailAgreeAcrossEveryStageAndReviewBoundary() throws Exception {
        long s=create(),a=item("공통 단계",false);
        check(a,"PENDING_REVIEW",null);
        request("POST","/api/admin/items/"+a+"/reject","{\"reason\":\"보완\"}");check(a,"REJECTED",null);
        long b=item("승인 단계",true);check(b,"APPROVED",null);lineup.replace(s,1,List.of(b));
        long lot=jdbc.queryForObject("SELECT id FROM lot",Long.class);check(b,"SCHEDULED",lot);
        for(String status:List.of("BIDDING","SOLD","UNSOLD")) {
            jdbc.update("UPDATE lot SET status=?,bidding_started_at=?,closed_at=?,highest_bidder_id=?,unsold_reason=?,current_price=60000,bid_count=?,last_bid_id=? WHERE id=?",status,
                Timestamp.from(BASE),status.equals("BIDDING")?null:Timestamp.from(BASE),status.equals("SOLD")?other:null,status.equals("UNSOLD")?"RESERVE_NOT_MET":null,1,1,lot);
            check(b,status.equals("BIDDING")?"ON_AUCTION":status,lot);
            var mine=body(request("GET","/api/me/items",null,"USER")).at("/items/0/currentLot");
            if(status.equals("BIDDING"))assertThat(mine.path("finalPrice").isNull()).isTrue();else assertThat(mine.path("finalPrice").asLong()).isEqualTo(60000);
            assertThat(mine.path("tradeStatus").isNull()).isTrue();
        }
        jdbc.update("UPDATE item SET reviewed_at=? WHERE id=?",Timestamp.from(BASE.plusMillis(1)),b);check(b,"APPROVED",null);
        jdbc.update("UPDATE item SET reviewed_at=? WHERE id=?",Timestamp.from(BASE),b);check(b,"UNSOLD",lot);
        // Equal createdAt uses higher lot ID, matching the existing admin contract.
        jdbc.update("INSERT INTO lot(lane_id,item_id,seq_no,status,start_price,reserve_price,current_price,bid_count,created_at,updated_at) VALUES(?,?,2,'PENDING',50000,917431000,50000,0,?,?)",laneId(s,1),b,Timestamp.from(BASE),Timestamp.from(BASE));
        long latest=jdbc.queryForObject("SELECT MAX(id) FROM lot",Long.class);check(b,"SCHEDULED",latest);
    }
    private void check(long id,String stage,Long lot) throws Exception {
        var mine=body(request("GET","/api/me/items",null,"USER")).path("items");
        var own=java.util.stream.StreamSupport.stream(mine.spliterator(),false).filter(r->r.path("itemId").asLong()==id).findFirst().orElseThrow();
        assertThat(own.path("stage").asText()).isEqualTo(stage);
        if(lot==null)assertThat(own.path("currentLot").isNull()).isTrue();else assertThat(own.at("/currentLot/lotId").asLong()).isEqualTo(lot);
        for(String filter:List.of("PENDING_REVIEW","REJECTED","APPROVED","SCHEDULED","ON_AUCTION","SOLD","UNSOLD")) {
            var rows=body(request("GET","/api/admin/items?stage="+filter,null)).path("items");
            boolean found=java.util.stream.StreamSupport.stream(rows.spliterator(),false).anyMatch(r->r.path("itemId").asLong()==id);
            assertThat(found).as(filter).isEqualTo(filter.equals(stage));
        }
        assertThat(body(request("GET","/api/admin/items/"+id,null)).path("stage").asText()).isEqualTo(stage);
    }
}
