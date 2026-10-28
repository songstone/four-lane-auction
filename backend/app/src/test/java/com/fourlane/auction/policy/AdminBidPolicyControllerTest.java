package com.fourlane.auction.policy;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fourlane.auction.policy.api.AdminBidPolicyController;
import com.fourlane.auction.policy.application.*;
import com.fourlane.global.config.*;
import com.fourlane.global.security.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminBidPolicyController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({ClockConfig.class, JacksonConfig.class, JwtConfig.class, JwtTokenIssuer.class, SecurityConfig.class, SecurityErrorHandler.class})
@ActiveProfiles("test")
class AdminBidPolicyControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenIssuer issuer;
    @Autowired Clock clock;
    @MockitoBean BidPolicyService policies;
    @Test void userAndAnonymousAreDeniedBeforeCallingTheService() throws Exception {
        String user = token("USER");
        mvc.perform(get("/api/admin/bid-policies").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bid-policies")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/bid-policies").header("Authorization", user).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(post("/api/admin/bid-policies").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(policies);
    }
    @Test void preservesRequiredNullsAndReturns201() throws Exception {
        var view = new BidPolicyView(1, 1, List.of(new BidPolicyView.Tier(0, 1000)), Instant.parse("2026-10-28T03:04:05Z"), null, true);
        when(policies.list()).thenReturn(List.of(view));
        when(policies.create(eq(1L), anyList())).thenReturn(view);
        mvc.perform(get("/api/admin/bid-policies").header("Authorization", token("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].createdByNickname").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-28T03:04:05.000Z"));
        mvc.perform(post("/api/admin/bid-policies").header("Authorization", token("ADMIN")).contentType("application/json")
                .content("{\"tiers\":[{\"minPrice\":0,\"increment\":1000}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isLatest").value(true));
    }
    private String token(String role) { return "Bearer " + issuer.issue(1, role, clock.instant()).value(); }
}
