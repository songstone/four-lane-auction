package com.fourlane.auction.session;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fourlane.auction.session.api.AdminSessionController;
import com.fourlane.auction.session.application.*;
import com.fourlane.global.config.*;
import com.fourlane.global.security.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(AdminSessionController.class) @AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import({ClockConfig.class,JacksonConfig.class,JwtConfig.class,JwtTokenIssuer.class,SecurityConfig.class,SecurityErrorHandler.class}) @ActiveProfiles("test")
class AdminSessionControllerTest {
    @Autowired MockMvc mvc;@Autowired JwtTokenIssuer issuer;@Autowired Clock clock;
    @MockitoBean AdminSessionService commands;@MockitoBean AdminSessionQueryService queries;
    private String token(String role){return "Bearer "+issuer.issue(1,role,clock.instant()).value();}
    @Test void deniesUnauthorizedRequestsBeforeServices()throws Exception{
        mvc.perform(post("/api/admin/sessions").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/sessions").header("Authorization",token("USER")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(commands,queries);
    }
    @Test void reportsMissingAndOffsetlessFieldsWithoutCallingService()throws Exception{
        mvc.perform(post("/api/admin/sessions").header("Authorization",token("ADMIN")).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("laneCount")).andExpect(jsonPath("$.error.fields[0].reason").value("REQUIRED"));
        mvc.perform(post("/api/admin/sessions").header("Authorization",token("ADMIN")).contentType("application/json").content("{\"laneCount\":1,\"scheduledStartAt\":\"2026-11-02T20:00:00\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].reason").value("MISSING_OFFSET"));
        mvc.perform(patch("/api/admin/sessions/1").header("Authorization",token("ADMIN")).contentType("application/json").content("{\"timing\":{\"softCloseMs\":null}}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("timing.softCloseMs")).andExpect(jsonPath("$.error.fields[0].reason").value("REQUIRED"));
        mvc.perform(post("/api/admin/sessions").header("Authorization",token("ADMIN")).contentType("application/json").content("null")).andExpect(status().isBadRequest());
        verifyNoInteractions(commands,queries);
    }
}
