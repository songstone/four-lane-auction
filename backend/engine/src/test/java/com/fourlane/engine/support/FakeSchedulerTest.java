package com.fourlane.engine.support;

import com.fourlane.engine.lane.LaneCommand;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FakeSchedulerTest {
    private static final Instant T0 = Instant.parse("2026-10-28T00:00:00Z");
    @Test void advanceExecutesByTimeThenRegistrationOrderIncludingNewReservations() {
        var clock = new FakeClock(T0);
        var scheduler = new FakeScheduler(clock);
        var first = new LaneCommand.CheckDeadline(1, 1);
        var second = new LaneCommand.CheckDeadline(2, 1);
        var third = new LaneCommand.EndGap(1, 1);
        scheduler.schedule(T0.plusSeconds(2), second);
        scheduler.schedule(T0.plusSeconds(1), first);
        scheduler.schedule(T0.plusSeconds(2), third);
        var seen = new ArrayList<LaneCommand>();
        var times = new ArrayList<Instant>();
        scheduler.advanceTo(T0.plusSeconds(3), command -> {
            seen.add(command);
            times.add(clock.instant());
            if (command.equals(first)) scheduler.schedule(T0.plusSeconds(3), new LaneCommand.Tick());
        });
        assertEquals(java.util.List.of(first, second, third, new LaneCommand.Tick()), seen);
        assertEquals(java.util.List.of(T0.plusSeconds(1), T0.plusSeconds(2), T0.plusSeconds(2), T0.plusSeconds(3)), times);
        assertTrue(scheduler.reservations().isEmpty());
    }

    @Test void clockJumpAloneDoesNotDispatchAndRunDueKeepsTheDelayedProcessingTime() {
        var clock = new FakeClock(T0);
        var scheduler = new FakeScheduler(clock);
        scheduler.schedule(T0.plusSeconds(1), new LaneCommand.Tick());
        clock.advance(Duration.ofSeconds(3));
        assertEquals(1, scheduler.reservations().size());
        var seen = new ArrayList<Instant>();
        scheduler.runDue(command -> seen.add(clock.instant()));
        assertEquals(java.util.List.of(T0.plusSeconds(3)), seen);
    }
}
