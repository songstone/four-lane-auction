package com.fourlane.support;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> now;
    public MutableClock(Instant now) { this.now = new AtomicReference<>(now); }
    public void set(Instant next) { now.set(next); }
    public void advance(Duration duration) { now.updateAndGet(t -> t.plus(duration)); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now.get(); }
}
