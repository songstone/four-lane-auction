package com.fourlane.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ClockConfigTest {
    @Test
    void providesOneUtcClockWithMillisecondPrecision() {
        try (var context = new AnnotationConfigApplicationContext(ClockConfig.class)) {
            assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
            Clock clock = context.getBean(Clock.class);
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(clock.instant().getNano() % 1_000_000).isZero();
        }
    }
}
