package com.fourlane.auction.session;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fourlane.auction.session.api.PublicSessionController;
import com.fourlane.auction.session.application.PublicSessionQueryService;
import com.fourlane.global.config.*;
import com.fourlane.global.security.*;
import com.fourlane.global.error.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(PublicSessionController.class)
@Import({JacksonConfig.class,ClockConfig.class,SecurityConfig.class,JwtConfig.class,SecurityErrorHandler.class})
@ActiveProfiles("test")
class PublicSessionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PublicSessionQueryService queries;
    @Test void missingSessionAndLotUseContractErrors() throws Exception {
        when(queries.session(7,null)).thenThrow(new ApiException(ErrorCode.SESSION_NOT_FOUND));
        when(queries.lot(8)).thenThrow(new ApiException(ErrorCode.LOT_NOT_FOUND));
        mvc.perform(get("/api/sessions/7")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        mvc.perform(get("/api/lots/8")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("LOT_NOT_FOUND"));
    }
    @Test void invalidIdsAndInvalidBearerAreRejected() throws Exception {
        mvc.perform(get("/api/sessions/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/lots/nope")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/sessions/7").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(queries);
    }
}
