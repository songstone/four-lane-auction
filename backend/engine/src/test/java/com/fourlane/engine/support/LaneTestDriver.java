package com.fourlane.engine.support;

import com.fourlane.engine.lane.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Test-only serial driver. Collects persistence/publication intentions; performs no DB or STOMP I/O. */
public final class LaneTestDriver {
    public final FakeClock clock;
    public final FakeScheduler scheduler;
    public final List<LaneDecision> decisions = new ArrayList<>();
    private final LaneStateMachine machine = new LaneStateMachine();
    private LaneState state;
    private Instant previousTime;

    public LaneTestDriver(LaneState initial, Instant at) {
        state = initial;
        clock = new FakeClock(at);
        scheduler = new FakeScheduler(clock);
    }
    public LaneState state() { return state; }
    public Instant previousTime() { return previousTime; }
    public LaneDecision send(LaneCommand command) {
        var now = LaneProcessingTime.read(clock, previousTime);
        var decision = machine.handle(state, command, now);
        previousTime = decision.processedAt();
        state = decision.nextState();
        decisions.add(decision);
        for (var effect : decision.effects())
            if (effect instanceof LaneEffect.ScheduleTimer timer)
                scheduler.schedule(timer.at(), timer.command());
        return decision;
    }
    public void advanceTo(Instant at) { scheduler.advanceTo(at, this::send); }
    public List<LaneEvent> events() {
        return decisions.stream().flatMap(d -> d.events().stream()).toList();
    }
}
