package com.fourlane.auction.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fourlane.auction.session.api.SessionController;
import com.fourlane.auction.session.application.SessionQueryService;
import com.fourlane.global.config.JacksonConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
@Import(JacksonConfig.class)
@ActiveProfiles("test")
class SessionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SessionQueryService queries;

    @BeforeEach
    void emptyResult() {
        when(queries.list(any(), anyInt(), nullable(String.class))).thenReturn(
                new SessionQueryService.Result(Instant.parse("2026-10-27T00:00:00Z"), List.of(), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCHEDULED", "RUNNING", "FINISHED"})
    void emptyResponseUsesExactContract(String state) throws Exception {
        mvc.perform(get("/api/sessions").param("status", state))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"serverTime":"2026-10-27T00:00:00.000Z","items":[],"nextCursor":null}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void missingStatusIsRequiredFieldError() throws Exception {
        mvc.perform(get("/api/sessions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fields[0].field").value("status"))
                .andExpect(jsonPath("$.error.fields[0].reason").value("REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"scheduled", "UNKNOWN", ""})
    void invalidStatusIsRejected(String value) throws Exception {
        mvc.perform(get("/api/sessions").param("status", value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @CsvSource({"0,OUT_OF_RANGE", "101,OUT_OF_RANGE", "-1,OUT_OF_RANGE", "abc,INVALID_FORMAT"})
    void invalidPageSizeUsesFieldErrors(String value, String reason) throws Exception {
        mvc.perform(get("/api/sessions").param("status", "SCHEDULED").param("size", value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fields[0].field").value("size"))
                .andExpect(jsonPath("$.error.fields[0].reason").value(reason));
    }
}
