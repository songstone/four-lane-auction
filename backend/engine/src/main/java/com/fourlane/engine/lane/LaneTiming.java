package com.fourlane.engine.lane;
import java.time.Duration;
public record LaneTiming(Duration firstBidWait, Duration softClose, Duration lotGap, Duration resumeMin) {}
