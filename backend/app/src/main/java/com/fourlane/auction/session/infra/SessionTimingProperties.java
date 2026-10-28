package com.fourlane.auction.session.infra;
import com.fourlane.auction.session.domain.SessionTiming;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
@Component @Validated @ConfigurationProperties(prefix="fourlane.auction.session.timing")
public class SessionTimingProperties implements com.fourlane.auction.session.domain.SessionTimingDefaults {
    @Min(1000) @Max(60000) private int firstBidWaitMs;
    @Min(1000) @Max(60000) private int softCloseMs;
    @Min(1000) @Max(60000) private int lotGapMs;
    @Min(1000) @Max(60000) private int resumeMinMs;
    public int getFirstBidWaitMs(){return firstBidWaitMs;} public void setFirstBidWaitMs(int v){firstBidWaitMs=v;}
    public int getSoftCloseMs(){return softCloseMs;} public void setSoftCloseMs(int v){softCloseMs=v;}
    public int getLotGapMs(){return lotGapMs;} public void setLotGapMs(int v){lotGapMs=v;}
    public int getResumeMinMs(){return resumeMinMs;} public void setResumeMinMs(int v){resumeMinMs=v;}
    public SessionTiming timing(){return SessionTiming.milliseconds(firstBidWaitMs,softCloseMs,lotGapMs,resumeMinMs);}
}
