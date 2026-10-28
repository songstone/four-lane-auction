package com.fourlane.auction.session;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fourlane.auction.session.api.AdminSessionLineupController;
import com.fourlane.auction.session.application.SessionLineupService;
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

@WebMvcTest(AdminSessionLineupController.class) @AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import({ClockConfig.class,JacksonConfig.class,JwtConfig.class,JwtTokenIssuer.class,SecurityConfig.class,SecurityErrorHandler.class}) @ActiveProfiles("test")
class SessionLineupControllerTest {
    @Autowired MockMvc mvc; @Autowired JwtTokenIssuer issuer; @Autowired Clock clock;
    @MockitoBean SessionLineupService service;
    private String token(String role) { return "Bearer " + issuer.issue(1, role, clock.instant()).value(); }
    @Test void authorizationAndDuplicateValidationPrecedeService() throws Exception {
        String path="/api/admin/sessions/1/lanes/1/lots";
        mvc.perform(put(path).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(put(path).header("Authorization",token("USER")).contentType("application/json").content("{\"itemIds\":[]}")).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization",token("ADMIN")).contentType("application/json").content("{\"itemIds\":[1,1]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].reason").value("DUPLICATE"));
        mvc.perform(put(path).header("Authorization",token("ADMIN")).contentType("application/json").content("null")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
