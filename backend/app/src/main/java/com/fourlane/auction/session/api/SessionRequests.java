package com.fourlane.auction.session.api;
import com.fourlane.auction.session.application.AdminSessionService.Input;
import com.fourlane.global.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import tools.jackson.databind.JsonNode;
public final class SessionRequests {
    private SessionRequests(){}
    @Schema(name="SessionTimingRequest")
    public record Timing(@Schema(minimum="1000",maximum="60000") Integer firstBidWaitMs,
        @Schema(minimum="1000",maximum="60000") Integer softCloseMs,@Schema(minimum="1000",maximum="60000") Integer lotGapMs,
        @Schema(minimum="1000",maximum="60000") Integer resumeMinMs){}
    @Schema(name="SessionCreateRequest",requiredProperties={"laneCount","scheduledStartAt"})
    public record Create(@Schema(implementation=Integer.class,minimum="1",maximum="4") JsonNode laneCount,
        @Schema(implementation=String.class,format="date-time") JsonNode scheduledStartAt,
        @Schema(implementation=Timing.class) JsonNode timing){
        public Input input(){return new Input(integer(laneCount,"laneCount",1,4,true),date(scheduledStartAt,true),timingValues(timing));}
    }
    @Schema(name="SessionPatchRequest")
    public record Patch(@Schema(implementation=String.class,format="date-time") JsonNode scheduledStartAt,
        @Schema(implementation=Timing.class) JsonNode timing){
        public Input input(){return new Input(null,date(scheduledStartAt,false),timingValues(timing));}
    }
    private static boolean absent(JsonNode n){return n==null || n.isMissingNode();}
    private static Integer integer(JsonNode n,String field,int min,int max,boolean required){
        if(absent(n)){if(required)throw ApiException.invalidRequest(field,"REQUIRED");return null;}
        if(n.isNull())throw ApiException.invalidRequest(field,"REQUIRED");
        if(!n.isIntegralNumber())throw ApiException.invalidRequest(field,"INVALID_FORMAT");
        if(!n.canConvertToInt() || n.intValue()<min || n.intValue()>max)throw ApiException.invalidRequest(field,"OUT_OF_RANGE");
        return n.intValue();
    }
    private static Map<String,Integer> timingValues(JsonNode n){
        if(absent(n))return Map.of();
        if(n.isNull())throw ApiException.invalidRequest("timing","REQUIRED");
        if(!n.isObject())throw ApiException.invalidRequest("timing","INVALID_FORMAT");
        var values=new HashMap<String,Integer>();
        for(String name:List.of("firstBidWaitMs","softCloseMs","lotGapMs","resumeMinMs")){
            var v=integer(n.get(name),"timing."+name,1000,60000,false);if(v!=null)values.put(name,v);
        }
        return Map.copyOf(values);
    }
    private static Instant date(JsonNode n,boolean required){
        if(absent(n)){if(required)throw ApiException.invalidRequest("scheduledStartAt","REQUIRED");return null;}
        if(n.isNull())throw ApiException.invalidRequest("scheduledStartAt","REQUIRED");
        if(!n.isString())throw ApiException.invalidRequest("scheduledStartAt","INVALID_FORMAT");
        String value=n.asString();
        try{
            Instant at=OffsetDateTime.parse(value).toInstant();
            if(at.getNano()%1_000_000!=0)throw ApiException.invalidRequest("scheduledStartAt","INVALID_FORMAT");
            if(at.compareTo(Instant.parse("1000-01-01T00:00:00Z"))<0 || at.compareTo(Instant.parse("9999-12-31T23:59:59.499Z"))>0)
                throw ApiException.invalidRequest("scheduledStartAt","OUT_OF_RANGE");
            return at;
        }catch(DateTimeParseException e){
            try{LocalDateTime.parse(value);throw ApiException.invalidRequest("scheduledStartAt","MISSING_OFFSET");}
            catch(DateTimeParseException ignored){throw ApiException.invalidRequest("scheduledStartAt","INVALID_FORMAT");}
        }
    }
}
