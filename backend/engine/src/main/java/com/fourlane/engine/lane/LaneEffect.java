package com.fourlane.engine.lane;
import java.time.Instant;
/** Data only. The driver must persist atomically before adopting state, publishing or scheduling. */
public sealed interface LaneEffect {
    /** Describes domain writes, not an event-store append. SOLD includes creation of its trade. */
    record PersistChange(LaneEvent change) implements LaneEffect {}
    record PublishEvent(LaneEvent event) implements LaneEffect {}
    record ScheduleTimer(Instant at, LaneCommand command) implements LaneEffect {}
}
