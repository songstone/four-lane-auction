package com.fourlane.global.config;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** One opaque identity per application context (server boot), shared by every lane. */
@Component
public final class ServerEpoch {
    private final String value = UUID.randomUUID().toString();
    public String value() { return value; }
}
