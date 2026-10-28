package com.fourlane.item.api;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ItemOpenApiConfiguration {
    @Bean OpenApiCustomizer nullableItemCurrentLot() {
        return document -> {
            if (document.getComponents() == null || document.getComponents().getSchemas() == null) return;
            var summary = document.getComponents().getSchemas().get("MyItemSummary");
            if (summary == null) return;
            // OpenAPI 3.1: type:null beside $ref is an intersection, not a nullable reference.
            summary.addProperty("currentLot", new ComposedSchema()
                    .addAnyOfItem(new Schema<>().$ref("#/components/schemas/MyItemCurrentLot"))
                    .addAnyOfItem(new Schema<>().types(java.util.Set.of("null"))));
        };
    }
}
