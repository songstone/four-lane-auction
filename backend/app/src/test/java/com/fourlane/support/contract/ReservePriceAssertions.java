package com.fourlane.support.contract;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Apply to actual response/captured-event JSON, never to request schemas containing reservePrice. */
public final class ReservePriceAssertions {
    private ReservePriceAssertions() {}
    public static void assertHidden(JsonNode actual, Set<Long> markers) { visit(actual, "$", markers); }
    public static void assertAdminItemDetail(JsonNode actual, long expected, Set<Long> markers) {
        assertThat(actual.isObject()).isTrue();
        assertThat(actual.path("reservePrice").isIntegralNumber()).isTrue();
        assertThat(actual.path("reservePrice").asLong()).isEqualTo(expected);
        var remaining = (tools.jackson.databind.node.ObjectNode) actual.deepCopy();
        remaining.remove("reservePrice");
        assertHidden(remaining, markers);
    }
    private static void visit(JsonNode node, String path, Set<Long> markers) {
        if (node.isObject()) {
            for (var entry : node.properties()) {
                String normalized = entry.getKey().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                assertThat(normalized).as("forbidden reserve field at %s.%s", path, entry.getKey()).isNotEqualTo("reserveprice");
                visit(entry.getValue(), path + "." + entry.getKey(), markers);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) visit(node.get(i), path + "[" + i + "]", markers);
        } else if (node.isNumber()) {
            for (long marker : markers) assertThat(node.asDouble()).as("reserve value at %s", path).isNotEqualTo((double) marker);
        } else if (node.isString()) {
            for (long marker : markers) assertThat(node.asText()).as("reserve string at %s", path)
                    .doesNotContain(Long.toString(marker), String.format(Locale.ROOT, "%,d", marker));
        }
    }
}
