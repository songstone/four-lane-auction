package com.fourlane.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fourlane.global.config.JacksonConfig;
import com.fourlane.global.error.ApiErrorResponse.LaneReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.RequestDispatcher;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiContractTest.ContractProbe.class)
@Import({JacksonConfig.class, ApiContractTest.ContractProbe.class, ApiErrorController.class})
@ActiveProfiles("test")
class ApiContractTest {
    @Autowired
    MockMvc mvc;

    @Test
    void missingPathUsesContractEnvelopeWithoutInternalDetails() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.lane").doesNotExist())
                .andExpect(jsonPath("$.error.fields").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void browserAcceptHeaderStillGetsJsonErrorContract() throws Exception {
        mvc.perform(get("/api/does-not-exist").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @CsvSource({
        "2026-10-23T11:00:00Z,2026-10-23T11:00:00.000Z",
        "2026-10-23T11:00:00.120Z,2026-10-23T11:00:00.120Z",
        "2026-10-23T20:00:00+09:00,2026-10-23T11:00:00.000Z"
    })
    void serializesInstantThroughMvcWithExactlyThreeUtcDigits(String input, String expected) throws Exception {
        mvc.perform(get("/api/test/time").param("value", input))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.at").value(expected))
                .andExpect(jsonPath("$.optional").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void fieldValidationUsesStableReasons() throws Exception {
        mvc.perform(post("/api/test/input").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fields[0].field").value("name"))
                .andExpect(jsonPath("$.error.fields[0].reason").value("REQUIRED"))
                .andExpect(jsonPath("$.error.lane").doesNotExist());
    }

    @Test
    void malformedJsonHasEmptyFieldsAndNoParserDetails() throws Exception {
        var result = mvc.perform(post("/api/test/input")
                        .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fields").isEmpty())
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Exception", "broken", "stackTrace", "trace");
    }

    @Test
    void businessRejectionCarriesLaneReferenceAndOmitsFields() throws Exception {
        mvc.perform(get("/api/test/rejection"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.error.lane.epoch").value("test-epoch"))
                .andExpect(jsonPath("$.error.lane.seq").value(59))
                .andExpect(jsonPath("$.error.fields").doesNotExist());
    }

    @Test
    void servletErrorDispatchUsesTheSameContract() throws Exception {
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void unexpectedExceptionReturnsSanitizedServerError() throws Exception {
        var result = mvc.perform(get("/api/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.fields").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("IllegalStateException", "private-database-detail", "stackTrace", "trace");
    }

    // Test-only endpoints exercise the production MVC configuration; no product API is added.
    @RestController
    static class ContractProbe {
        @GetMapping("/api/test/time")
        TimeResponse time(@org.springframework.web.bind.annotation.RequestParam String value) {
            return new TimeResponse(Instant.parse(value), null);
        }

        @PostMapping("/api/test/input")
        Input input(@Valid @RequestBody Input input) {
            return input;
        }

        @GetMapping("/api/test/rejection")
        void rejection() {
            throw new ApiException(ErrorCode.PRICE_CHANGED, new LaneReference("test-epoch", 59));
        }

        @GetMapping("/api/test/failure")
        void failure() {
            throw new IllegalStateException("private-database-detail");
        }
    }

    record TimeResponse(Instant at, String optional) {}
    record Input(@NotBlank String name) {}
}
