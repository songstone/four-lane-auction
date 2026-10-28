package com.fourlane.auction.session;
import static org.assertj.core.api.Assertions.*;
import com.fourlane.auction.session.api.SessionRequests;
import com.fourlane.global.error.ApiException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
class SessionValidationTest {
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void distinguishesOmittedFieldsFromExplicitNull(){
        assertThat(json.readValue("{}",SessionRequests.Patch.class).input().timing()).isEmpty();
        for(String value:new String[]{"{\"scheduledStartAt\":null}","{\"timing\":null}","{\"timing\":{\"softCloseMs\":null}}"})
            assertThatThrownBy(()->json.readValue(value,SessionRequests.Patch.class).input()).isInstanceOf(ApiException.class);
    }
    @ParameterizedTest @CsvSource({"999,false","1000,true","1001,true","59999,true","60000,true","60001,false","0,false","-1,false"})
    void validatesTimingBounds(int value,boolean valid){
        for(String name:new String[]{"firstBidWaitMs","softCloseMs","lotGapMs","resumeMinMs"}){
            String body="{\"timing\":{\""+name+"\":"+value+"}}";
            if(valid)assertThat(json.readValue(body,SessionRequests.Patch.class).input().timing()).containsEntry(name,value);
            else assertThatThrownBy(()->json.readValue(body,SessionRequests.Patch.class).input()).isInstanceOf(ApiException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings={"1.5","\"1000\"","true","9223372036854775808","null"})
    void refusesCoercedTimingValues(String value){assertThatThrownBy(()->json.readValue("{\"timing\":{\"softCloseMs\":"+value+"}}",SessionRequests.Patch.class).input()).isInstanceOf(ApiException.class);}
    @ParameterizedTest @ValueSource(strings={"0","5","1.5","\"2\"","true","null"})
    void rejectsInvalidLaneCount(String value){assertThatThrownBy(()->json.readValue("{\"laneCount\":"+value+",\"scheduledStartAt\":\"2026-11-02T20:00:00+09:00\"}",SessionRequests.Create.class).input()).isInstanceOf(ApiException.class);}
    @ParameterizedTest @ValueSource(strings={"2026-11-02T20:00:00","2026-11-02T20:00:00.000001Z","garbage","+10000-01-01T00:00:00Z"})
    void rejectsUnrepresentableOrOffsetlessDates(String value){assertThatThrownBy(()->json.readValue("{\"scheduledStartAt\":\""+value+"\"}",SessionRequests.Patch.class).input()).isInstanceOf(ApiException.class);}
    @Test void parsesOffsetWithoutChangingTheInstant(){assertThat(json.readValue("{\"scheduledStartAt\":\"2026-11-02T20:00:00.123000+09:00\"}",SessionRequests.Patch.class).input().scheduledStartAt()).isEqualTo(Instant.parse("2026-11-02T11:00:00.123Z"));}
}
