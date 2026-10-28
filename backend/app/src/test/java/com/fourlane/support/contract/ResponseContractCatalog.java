package com.fourlane.support.contract;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.*;

/** One executable case per response/audience; every product REST handler must have a case. */
public final class ResponseContractCatalog {
    @FunctionalInterface public interface RestCall { MvcResult execute() throws Exception; }
    @FunctionalInterface public interface EventCapture { JsonNode receive() throws Exception; }
    record RestCase(String method, String path, String audience, int status, boolean binary, Long adminReserve, RestCall call) {}
    record EventCase(String type, String audience, EventCapture capture) {}
    private final List<RestCase> rest = new ArrayList<>();
    private final List<EventCase> events = new ArrayList<>();
    public void rest(String method, String path, String audience, int status, RestCall call) {
        rest.add(new RestCase(method, path, audience, status, false, null, call));
    }
    public void binary(String method, String path, String audience, RestCall call) {
        rest.add(new RestCase(method, path, audience, 200, true, null, call));
    }
    public void adminItemDetail(long expectedReserve, RestCall call) {
        rest.add(new RestCase("GET", "/api/admin/items/{itemId}", "ADMIN", 200, false, expectedReserve, call));
    }
    public void event(String type, String audience, EventCapture actualCapture) { events.add(new EventCase(type, audience, actualCapture)); }
    public void verifyRestCoverage(RequestMappingHandlerMapping mappings) {
        var actual = new TreeSet<String>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.fourlane")) return;
            for (String path : mapping.getPatternValues()) if (path.startsWith("/api/"))
                for (var method : mapping.getMethodsCondition().getMethods()) actual.add(method.name() + " " + path);
        });
        var registered = new TreeSet<String>();
        rest.forEach(entry -> registered.add(entry.method() + " " + entry.path()));
        assertThat(registered).as("All REST handlers must register executable response cases").isEqualTo(actual);
    }
    public void verifyEventCoverage(Set<String> implementedTypes) {
        assertThat(events.stream().map(EventCase::type).collect(java.util.stream.Collectors.toSet())).isEqualTo(implementedTypes);
    }
    public void verify(ObjectMapper json, Set<Long> markers) throws Exception {
        for (var entry : rest) {
            var response = entry.call().execute().getResponse();
            assertThat(response.getStatus()).as("%s %s audience=%s", entry.method(), entry.path(), entry.audience()).isEqualTo(entry.status());
            if (entry.binary()) {
                assertThat(response.getContentType()).startsWith("image/");
                assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            } else if (response.getContentAsByteArray().length > 0) {
                assertThat(response.getContentType()).startsWith("application/json");
                var actual = json.readTree(response.getContentAsString());
                if (entry.adminReserve() == null) ReservePriceAssertions.assertHidden(actual, markers);
                else ReservePriceAssertions.assertAdminItemDetail(actual, entry.adminReserve(), markers);
            }
        }
        for (var entry : events) ReservePriceAssertions.assertHidden(entry.capture().receive(), markers);
    }
}
