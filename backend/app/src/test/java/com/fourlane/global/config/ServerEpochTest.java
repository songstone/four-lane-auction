package com.fourlane.global.config;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ServerEpochTest {
    @Test void stableWithinBootAndDifferentAcrossBoots() {
        var first = new ServerEpoch();
        assertThat(first.value()).isNotBlank().isEqualTo(first.value()).isNotEqualTo(new ServerEpoch().value());
    }
}
