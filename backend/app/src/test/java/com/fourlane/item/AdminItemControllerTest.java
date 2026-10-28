package com.fourlane.item;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fourlane.global.config.*;
import com.fourlane.global.security.*;
import com.fourlane.item.api.AdminItemController;
import com.fourlane.item.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminItemController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({ClockConfig.class, JacksonConfig.class, JwtConfig.class, JwtTokenIssuer.class, SecurityConfig.class, SecurityErrorHandler.class})
@ActiveProfiles("test")
class AdminItemControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenIssuer issuer;
    @Autowired java.time.Clock clock;
    @MockitoBean AdminItemQueryService queries;
    @MockitoBean ItemReviewService reviews;
    @Test void userAndAnonymousCannotReadOrChangeReviewData() throws Exception {
        String user = "Bearer " + issuer.issue(1, "USER", clock.instant()).value();
        for (var action : new String[]{"approve", "reject"}) {
            var path = "/api/admin/items/1/" + action;
            mvc.perform(post(path).header("Authorization", user).contentType("application/json").content("{\"reason\":\"확인 불가\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
            mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        }
        for (var path : new String[]{"/api/admin/items?stage=PENDING_REVIEW", "/api/admin/items/1"}) {
            mvc.perform(get(path).header("Authorization", user)).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(queries, reviews);
    }
}
