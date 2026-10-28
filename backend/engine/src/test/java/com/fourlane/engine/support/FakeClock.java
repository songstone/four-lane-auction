package com.fourlane.engine.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit wall-clock control; moving this clock does not run any timers. */
public final class FakeClock extends Clock {
    private final AtomicReference<Instant> value;
    private final ZoneId zone;

    public FakeClock(Instant initial) { this(new AtomicReference<>(initial), ZoneOffset.UTC); }
    private FakeClock(AtomicReference<Instant> value, ZoneId zone) {
        this.value = value;
        this.zone = Objects.requireNonNull(zone);
    }
    public void set(Instant next) { value.set(Objects.requireNonNull(next)); }
    public void advance(Duration duration) { value.updateAndGet(at -> at.plus(duration)); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new FakeClock(value, zone); }
    @Override public Instant instant() { return value.get(); }
}
