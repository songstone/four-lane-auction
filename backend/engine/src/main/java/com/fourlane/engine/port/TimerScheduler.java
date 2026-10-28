package com.fourlane.engine.port;
import com.fourlane.engine.lane.LaneCommand;
import java.time.Instant;
/** Reserves a command for dispatch. State decisions never run on behalf of this port. */
public interface TimerScheduler {
    void schedule(Instant at, LaneCommand command);
}
