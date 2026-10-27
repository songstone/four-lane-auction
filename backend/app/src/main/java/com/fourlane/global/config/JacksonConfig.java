package com.fourlane.global.config;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

@Configuration(proxyBeanMethods = false)
public class JacksonConfig {
    private static final DateTimeFormatter UTC_MILLIS =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    @Bean
    public JsonMapperBuilderCustomizer utcMillisCustomizer() {
        SimpleModule module = new SimpleModule("utc-milliseconds");
        module.addSerializer(Instant.class, new StdSerializer<Instant>(Instant.class) {
            @Override
            public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
                generator.writeString(UTC_MILLIS.format(value));
            }
        });
        return builder -> builder.addModule(module);
    }
}
