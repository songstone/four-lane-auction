package com.fourlane.item;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fourlane.global.config.*;
import com.fourlane.global.security.*;
import com.fourlane.item.api.*;
import com.fourlane.item.application.*;
import com.fourlane.support.ImageFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ItemController.class, MyItemController.class, ImageController.class})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(
        print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import({ClockConfig.class, JacksonConfig.class, JwtConfig.class, JwtTokenIssuer.class, SecurityConfig.class, SecurityErrorHandler.class})
@ActiveProfiles("test")
class ItemControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenIssuer issuer;
    @Autowired java.time.Clock clock;
    @MockitoBean ItemRegistrationService registration;
    @MockitoBean ItemQueryService queries;
    String bearer(String role) { return "Bearer " + issuer.issue(1, role, clock.instant()).value(); }
    @Test void rejectsMissingImages() throws Exception {
        // Validation is in the application service; this slice verifies the multipart binding and security path.
        doThrow(com.fourlane.global.error.ApiException.invalidRequest("images", "REQUIRED"))
                .when(registration).register(eq(1L), any(), eq(java.util.List.of()));
        mvc.perform(multipart("/api/items").param("name", "상품").param("startPrice", "1000").header("Authorization", bearer("USER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].reason").value("REQUIRED"));
    }
    @Test void forbidsAdminRegistrationAndAnonymousRegistration() throws Exception {
        var image = new MockMultipartFile("images", "same.jpg", "image/jpeg", ImageFixtures.PNG);
        mvc.perform(multipart("/api/items").file(image).param("name", "상품").param("startPrice", "1000").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(multipart("/api/items").file(image)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/items").header("Authorization", bearer("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/items")).andExpect(status().isUnauthorized());
        verifyNoInteractions(registration, queries);
    }
    @Test void badFieldFormatDoesNotReachStorage() throws Exception {
        mvc.perform(multipart("/api/items").param("startPrice", "1.5").header("Authorization", bearer("USER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].reason").value("INVALID_FORMAT"));
        verifyNoInteractions(registration);
    }
}
