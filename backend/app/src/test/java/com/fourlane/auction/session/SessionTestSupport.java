package com.fourlane.auction.session;
import com.fourlane.item.ItemTestSupport;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
abstract class SessionTestSupport extends ItemTestSupport {
    protected static final String VALID="{\"laneCount\":2,\"scheduledStartAt\":\"2026-11-02T20:00:00.123+09:00\"}";
    protected HttpResponse<String> request(String method,String path,String body) throws Exception{return request(method,path,body,"ADMIN");}
    protected HttpResponse<String> request(String method,String path,String body,String role) throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
        if(role!=null)b.header("Authorization","Bearer "+token(role.equals("ADMIN")?admin:seller,role));
        if(body!=null)b.header("Content-Type","application/json");
        return CLIENT.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    protected long create() throws Exception{
        var r=request("POST","/api/admin/sessions",VALID);
        org.assertj.core.api.Assertions.assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return body(r).at("/session/sessionId").asLong();
    }
    protected JsonNode detail(long id) throws Exception{return body(request("GET","/api/admin/sessions/"+id,null));}
}
