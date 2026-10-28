package com.fourlane.auction.session.api;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class PublicSessionOpenApiConfiguration {
    @Bean OpenApiCustomizer nullablePublicSessionObjects() {
        return api -> {
            var schemas = api.getComponents().getSchemas();
            String[][] fields = {
                {"PublicSessionResponse","me","PublicSessionMe"},
                {"PublicLaneSnapshot","me","PublicLaneMe"},
                {"PublicLaneSnapshot","lotInfo","PublicLotInfo"},
                {"PublicLaneSnapshot","nextLotInfo","PublicLotInfo"},
                {"PublicLaneState","lot","PublicLotState"}
            };
            for (var field : fields) {
                var parent = schemas.get(field[0]);
                if (parent != null && parent.getProperties() != null) {
                    parent.addProperty(field[1], new ComposedSchema()
                        .addAnyOfItem(new Schema<>().$ref("#/components/schemas/" + field[2]))
                        .addAnyOfItem(new Schema<>().types(java.util.Set.of("null"))));
                }
            }
        };
    }
}
