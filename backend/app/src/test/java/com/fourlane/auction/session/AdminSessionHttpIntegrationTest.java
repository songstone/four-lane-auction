package com.fourlane.auction.session;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
class AdminSessionHttpIntegrationTest extends SessionTestSupport {
    @ParameterizedTest @ValueSource(ints={1,2,3,4})
    void createsScheduledSessionWithReadyLanes(int count)throws Exception{
        var r=request("POST","/api/admin/sessions",VALID.replace("\"laneCount\":2","\"laneCount\":"+count));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);var v=body(r);long id=v.at("/session/sessionId").asLong();
        assertThat(v.at("/session/status").asText()).isEqualTo("SCHEDULED");assertThat(v.at("/lanes").size()).isEqualTo(count);
        for(int i=0;i<count;i++){assertThat(v.at("/lanes/"+i+"/laneNo").asInt()).isEqualTo(i+1);assertThat(v.at("/lanes/"+i+"/laneStatus").asText()).isEqualTo("READY");assertThat(v.at("/lanes/"+i+"/lots").isEmpty()).isTrue();}
        assertThat(v.at("/emptyLaneNos").size()).isEqualTo(count);assertThat(v.path("startable").asBoolean()).isFalse();
        assertThat(v.at("/session/bidPolicyVersion").isNull()).isTrue();
        assertThat(v.at("/session/timing/firstBidWaitMs").asInt()).isEqualTo(15000);
        assertThat(v.at("/session/timing/softCloseMs").asInt()).isEqualTo(10000);
        assertThat(v.at("/session/timing/lotGapMs").asInt()).isEqualTo(5000);
        assertThat(v.at("/session/timing/resumeMinMs").asInt()).isEqualTo(10000);
        assertThat(body(request("GET","/api/sessions?status=SCHEDULED",null,null)).at("/items/0/sessionId").asLong()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT created_at FROM auction_session WHERE id=?",Timestamp.class,id).toInstant()).isEqualTo(BASE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lane WHERE session_id=? AND created_at=? AND updated_at=?",Integer.class,id,Timestamp.from(BASE),Timestamp.from(BASE))).isEqualTo(count);
    }
    @Test void storesOffsetScheduleAsUtc()throws Exception{
        long id=create();assertThat(detail(id).at("/session/scheduledStartAt").asText()).isEqualTo("2026-11-02T11:00:00.123Z");
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(scheduled_start_at,'%Y-%m-%dT%H:%i:%s.%f') FROM auction_session WHERE id=?",String.class,id)).isEqualTo("2026-11-02T11:00:00.123000");
    }
    @ParameterizedTest @ValueSource(longs={-1,0,1})
    void checksScheduleAgainstInjectedClock(long offset)throws Exception{
        String time=BASE.plusMillis(offset).toString();String create="{\"laneCount\":1,\"scheduledStartAt\":\""+time+"\"}";
        var response=request("POST","/api/admin/sessions",create);assertThat(response.statusCode()).isEqualTo(offset>0?201:422);
        long id=create();response=request("PATCH","/api/admin/sessions/"+id,"{\"scheduledStartAt\":\""+time+"\"}");
        assertThat(response.statusCode()).isEqualTo(offset>0?200:422);
        if(offset<=0)assertThat(body(response).at("/error/code").asText()).isEqualTo("SCHEDULED_START_IN_PAST");
    }
    @Test void patchesOnlyProvidedFieldsAndPreservesEmptyRequestTimestamp()throws Exception{
        long id=create();var old=jdbc.queryForMap("SELECT * FROM auction_session WHERE id=?",id);
        when(clock.instant()).thenReturn(BASE.plusSeconds(10));
        for(String input:List.of("{}","{\"timing\":{}}")){assertThat(request("PATCH","/api/admin/sessions/"+id,input).statusCode()).isEqualTo(200);assertThat(jdbc.queryForMap("SELECT * FROM auction_session WHERE id=?",id)).isEqualTo(old);}
        var r=request("PATCH","/api/admin/sessions/"+id,"{\"timing\":{\"softCloseMs\":1234}}");assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(body(r).at("/session/timing/softCloseMs").asInt()).isEqualTo(1234);assertThat(body(r).at("/session/timing/lotGapMs").asInt()).isEqualTo(5000);
        jdbc.update("UPDATE auction_session SET scheduled_start_at=? WHERE id=?",Timestamp.from(BASE.minusMillis(1)),id);
        assertThat(request("PATCH","/api/admin/sessions/"+id,"{\"timing\":{\"lotGapMs\":2000}}").statusCode()).isEqualTo(200);
    }
    @Test void fillsPartiallyOmittedTimingFromConfiguration()throws Exception{
        var r=request("POST","/api/admin/sessions",VALID.substring(0,VALID.length()-1)+",\"timing\":{\"softCloseMs\":1001}}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);assertThat(body(r).at("/session/timing/softCloseMs").asInt()).isEqualTo(1001);assertThat(body(r).at("/session/timing/firstBidWaitMs").asInt()).isEqualTo(15000);
    }
    @Test void guardsRolesMissingIdsStartedStatesAndMalformedInputs()throws Exception{
        for(String method:List.of("GET","PATCH","POST")){
            String path="/api/admin/sessions"+(method.equals("POST")?"":"/1");String input=method.equals("POST")?VALID:method.equals("PATCH")?"{}":null;
            assertThat(request(method,path,input,null).statusCode()).isEqualTo(401);assertThat(request(method,path,input,"USER").statusCode()).isEqualTo(403);
        }
        for(String method:List.of("GET","PATCH"))assertThat(request(method,"/api/admin/sessions/9000000000",method.equals("PATCH")?"{}":null).statusCode()).isEqualTo(404);
        long id=create();
        for(String state:List.of("RUNNING","FINISHED")){jdbc.update("UPDATE auction_session SET status=?,bid_policy_id=1 WHERE id=?",state,id);var r=request("PATCH","/api/admin/sessions/"+id,"{}");assertThat(r.statusCode()).isEqualTo(422);assertThat(body(r).at("/error/code").asText()).isEqualTo("SESSION_NOT_SCHEDULED");}
        for(String input:List.of("{}","{\"laneCount\":0}",VALID.replace("+09:00",""),VALID.replace(".123+09:00",".123001+09:00"),VALID.substring(0,VALID.length()-1)+",\"timing\":null}")){
            var r=request("POST","/api/admin/sessions",input);assertThat(r.statusCode()).as(r.body()).isEqualTo(400);assertThat(body(r).at("/error/fields").isArray()).isTrue();
        }
    }
    @Test void readsExistingLotsAndCalculatesStartabilityWithoutExposingReserve()throws Exception{
        long id=create();
        var upload=upload(seller,"USER",Map.of("name","편성 조회 검증","startPrice","50000","reservePrice","917431000"),List.of(com.fourlane.support.ImageFixtures.PNG));
        assertThat(upload.statusCode()).isEqualTo(201);long itemId=body(upload).path("itemId").asLong();
        var lanes=jdbc.queryForList("SELECT id FROM lane WHERE session_id=? ORDER BY lane_no",Long.class,id);
        for(int i=0;i<lanes.size();i++){
            jdbc.update("INSERT INTO lot(lane_id,item_id,seq_no,status,start_price,reserve_price,current_price,bid_count,created_at,updated_at) VALUES(?,?,1,'PENDING',50000,917431000,50000,0,?,?)",lanes.get(i),itemId,Timestamp.from(BASE),Timestamp.from(BASE));
            var v=detail(id);assertThat(v.path("startable").asBoolean()).isEqualTo(i==lanes.size()-1);
        }
        var v=detail(id);assertThat(v.path("emptyLaneNos").isEmpty()).isTrue();assertThat(v.at("/lanes/0/lots/0/itemId").asLong()).isEqualTo(itemId);
        com.fourlane.support.contract.ReservePriceAssertions.assertHidden(v,Set.of(917431000L));
        assertThat(body(request("GET","/api/sessions?status=SCHEDULED",null,null)).at("/items/0/lotCount").asInt()).isEqualTo(2);
    }
    @Test void dbCheckRejectsOutOfBounds()throws Exception{
        long id=create();for(String field:List.of("first_bid_wait_ms","soft_close_ms","lot_gap_ms","resume_min_ms"))for(int value:List.of(999,60001))
            assertThatThrownBy(()->jdbc.update("UPDATE auction_session SET "+field+"=? WHERE id=?",value,id)).isInstanceOf(org.springframework.dao.DataAccessException.class).hasRootCauseInstanceOf(java.sql.SQLException.class).satisfies(e->assertThat(((java.sql.SQLException)e.getCause()).getErrorCode()).isEqualTo(3819));
    }
    @Test void openApiExposesAllAdminSessionOperationsAndRequiredNullableFields()throws Exception{
        var api=body(request("GET","/v3/api-docs",null,null));
        assertThat(api.at("/paths/~1api~1admin~1sessions/post/operationId").asText()).isEqualTo("createSession");
        assertThat(api.at("/paths/~1api~1admin~1sessions~1{sessionId}/patch/operationId").asText()).isEqualTo("patchSession");
        assertThat(api.at("/components/schemas/AdminSession/required").toString()).contains("bidPolicyVersion","startedAt","timing");
        assertThat(api.at("/components/schemas/AdminSession/properties/bidPolicyVersion/type").toString()).contains("null","integer");
    }
}
