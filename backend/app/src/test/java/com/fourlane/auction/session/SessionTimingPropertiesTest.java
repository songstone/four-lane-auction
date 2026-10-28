package com.fourlane.auction.session;
import static org.assertj.core.api.Assertions.*;
import com.fourlane.auction.session.infra.SessionTimingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
class SessionTimingPropertiesTest {
    @Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(SessionTimingProperties.class) static class Config{}
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(Config.class)
        .withPropertyValues("fourlane.auction.session.timing.first-bid-wait-ms=15000","fourlane.auction.session.timing.soft-close-ms=10000",
            "fourlane.auction.session.timing.lot-gap-ms=5000","fourlane.auction.session.timing.resume-min-ms=10000");
    @Test void usesConfiguredDefaultsAndRejectsInvalidConfiguration(){
        runner.withPropertyValues("fourlane.auction.session.timing.first-bid-wait-ms=1234").run(c->assertThat(c.getBean(SessionTimingProperties.class).timing().firstBidWait().toMillis()).isEqualTo(1234));
        runner.withPropertyValues("fourlane.auction.session.timing.lot-gap-ms=999").run(c->assertThat(c).hasFailed());
    }
}
