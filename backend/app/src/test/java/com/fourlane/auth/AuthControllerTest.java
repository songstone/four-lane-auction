package com.fourlane.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fourlane.auth.api.*;
import com.fourlane.auth.application.*;
import com.fourlane.global.config.*;
import com.fourlane.global.security.*;
import com.fourlane.member.application.MemberView;
import com.fourlane.member.domain.MemberRole;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.*;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(
        print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import({ClockConfig.class, JacksonConfig.class, JwtConfig.class, SecurityConfig.class,
        SecurityErrorHandler.class, RefreshTokenCookie.class})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean AuthService auth;
    final MemberView member = new MemberView(1, "test@example.com", "테스트", MemberRole.USER, false, 0);

    @BeforeEach void stubSuccess() {
        when(auth.signup(anyString(), anyString(), anyString())).thenReturn(member);
        when(auth.login(anyString(), anyString())).thenReturn(new AuthResult("test-only-response-token",
                Instant.parse("2026-10-27T00:15:00Z"), "test-only-cookie", 604800, member));
    }

    @ParameterizedTest @ValueSource(ints = {7, 65})
    void passwordCharacterBoundsAreFieldErrors(int length) throws Exception {
        for (String path : new String[]{"signup", "login"}) {
            mvc.perform(post("/api/auth/" + path).contentType("application/json").content(payload("a".repeat(length), "테스트")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("password"));
        }
        verifyNoInteractions(auth);
    }

    @Test void bcryptByteBoundaryAndUnicodeCodePointsAreValidatedBeforeEncoding() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(payload("한".repeat(24), "😀😀")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(payload("한".repeat(25), "테스트")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("password"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(payload("한".repeat(25), "테스트")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(payload("a".repeat(64), "😀".repeat(20))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(payload("password1", "😀".repeat(21))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("nickname"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "a"})
    void tooShortNicknameIsRejected(String nickname) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(payload("password1", nickname)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("nickname"));
    }

    @Test void malformedJsonValidationAuthenticationAndUnexpectedFailuresDoNotLogSecrets(CapturedOutput output) throws Exception {
        String password = "sensitive-test-password-marker";
        String token = "sensitive-test-token-marker";
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"password\":\"" + password + "\""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(payload("한".repeat(25), "테스트")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sessions?status=SCHEDULED").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        when(auth.login(anyString(), anyString())).thenThrow(new IllegalStateException(password + " " + token));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(payload(password, "테스트")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
        assertThat(output.getAll().contains(password)).isFalse();
        assertThat(output.getAll().contains(token)).isFalse();
        assertThat(new AuthRequests.Login("test@example.com", password).toString().contains(password)).isFalse();
    }

    @Test void prodCannotTurnOffSecureCookieEvenWithAPropertyOverride() {
        var environment = new org.springframework.mock.env.MockEnvironment();
        environment.setActiveProfiles("prod");
        var cookie = new RefreshTokenCookie(new AuthProperties("unused", false), environment);
        assertThat(cookie.value("test-only", 10)).contains("Secure", "HttpOnly", "SameSite=Strict");
        assertThat(cookie.delete()).contains("Secure", "Max-Age=0");
    }

    private String payload(String password, String nickname) throws Exception {
        return json.writeValueAsString(Map.of("email", "test@example.com", "password", password, "nickname", nickname));
    }
}
