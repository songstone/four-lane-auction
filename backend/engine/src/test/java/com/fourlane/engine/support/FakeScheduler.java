package com.fourlane.engine.support;

import com.fourlane.engine.lane.LaneCommand;
import com.fourlane.engine.port.TimerScheduler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/** Deterministic reservation order; overdue execution never rewinds the clock. */
public final class FakeScheduler implements TimerScheduler {
    public record Reservation(Instant at, long order, LaneCommand command) {}
    private static final Comparator<Reservation> ORDER =
            Comparator.comparing(Reservation::at).thenComparingLong(Reservation::order);
    private final PriorityQueue<Reservation> pending = new PriorityQueue<>(ORDER);
    private final FakeClock clock;
    private long nextOrder;

    public FakeScheduler(FakeClock clock) { this.clock = clock; }
    @Override public void schedule(Instant at, LaneCommand command) {
        pending.add(new Reservation(at, nextOrder++, command));
    }
    public List<Reservation> reservations() {
        var copy = new ArrayList<>(pending);
        copy.sort(ORDER);
        return List.copyOf(copy);
    }
    public void advanceTo(Instant target, Consumer<LaneCommand> dispatch) {
        if (target.isBefore(clock.instant())) throw new IllegalArgumentException("Use clock.set for a wall-clock jump");
        while (!pending.isEmpty() && !pending.peek().at().isAfter(target)) {
            var task = pending.remove();
            if (task.at().isAfter(clock.instant())) clock.set(task.at());
            dispatch.accept(task.command());
        }
        clock.set(target);
    }
    /** Runs at the clock's current time, allowing deliberate scheduling delay. */
    public void runDue(Consumer<LaneCommand> dispatch) {
        while (!pending.isEmpty() && !pending.peek().at().isAfter(clock.instant()))
            dispatch.accept(pending.remove().command());
    }
}
