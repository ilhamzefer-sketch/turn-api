package az.turn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class SessionSecurityIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void rejectsAccessImmediatelyAfterIdleTimeout() throws Exception {
        TestCsrfToken csrf = csrf();
        MvcResult registration = register(csrf, "0507001001");
        String accessToken = accessToken(registration);
        RefreshTokenEntity session = session("+994507001001");
        session.setIdleExpiresAt(LocalDateTime.now().minusSeconds(1));
        session.setExpiresAt(session.getIdleExpiresAt());
        refreshTokenRepository.saveAndFlush(session);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_IDLE_TIMEOUT"));

        RefreshTokenEntity expired = refreshTokenRepository.findById(session.getId()).orElseThrow();
        assertThat(expired.isRevoked()).isTrue();
        assertThat(expired.getRevokeReason()).isEqualTo(SessionRevocationReason.IDLE_TIMEOUT);
        assertThat(expired.getRevokedAt()).isNotNull();
    }

    @Test
    void rejectsRefreshAfterIdleTimeoutAndClearsCookie() throws Exception {
        TestCsrfToken csrf = csrf();
        MvcResult registration = register(csrf, "0507001002");
        Cookie refreshCookie = refreshCookie(registration);
        RefreshTokenEntity session = session("+994507001002");
        session.setIdleExpiresAt(LocalDateTime.now().minusSeconds(1));
        session.setExpiresAt(session.getIdleExpiresAt());
        refreshTokenRepository.saveAndFlush(session);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrf.cookie(), refreshCookie)
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_IDLE_TIMEOUT"))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("refresh_token=;"))));
    }

    @Test
    void userActivityExtendsOnlyIdleDeadline() throws Exception {
        TestCsrfToken csrf = csrf();
        MvcResult registration = register(csrf, "0507001003");
        String accessToken = accessToken(registration);
        RefreshTokenEntity session = session("+994507001003");
        LocalDateTime absoluteDeadline = LocalDateTime.now().plusMinutes(10);
        session.setIdleExpiresAt(LocalDateTime.now().plusSeconds(5));
        session.setExpiresAt(session.getIdleExpiresAt());
        session.setAbsoluteExpiresAt(absoluteDeadline);
        refreshTokenRepository.saveAndFlush(session);

        mockMvc.perform(post("/api/auth/activity")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idleExpiresAt").isString())
                .andExpect(jsonPath("$.absoluteExpiresAt").isString());

        RefreshTokenEntity updated = refreshTokenRepository.findById(session.getId()).orElseThrow();
        assertThat(updated.getIdleExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(9));
        assertThat(updated.getIdleExpiresAt()).isEqualTo(absoluteDeadline);
        assertThat(updated.getAbsoluteExpiresAt()).isEqualTo(absoluteDeadline);
    }

    @Test
    void refreshRotationNeverExtendsAbsoluteDeadline() throws Exception {
        TestCsrfToken csrf = csrf();
        MvcResult registration = register(csrf, "0507001004");
        Cookie refreshCookie = refreshCookie(registration);
        RefreshTokenEntity session = session("+994507001004");
        LocalDateTime absoluteDeadline = LocalDateTime.now().plusHours(1).withNano(0);
        session.setAbsoluteExpiresAt(absoluteDeadline);
        refreshTokenRepository.saveAndFlush(session);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrf.cookie(), refreshCookie)
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.absoluteExpiresAt").isString());

        RefreshTokenEntity rotated = refreshTokenRepository.findById(session.getId()).orElseThrow();
        assertThat(rotated.getAbsoluteExpiresAt()).isEqualTo(absoluteDeadline);
    }

    @Test
    void credentialChangeInvalidatesExistingAccessToken() throws Exception {
        TestCsrfToken csrf = csrf();
        MvcResult registration = register(csrf, "0507001005");
        String accessToken = accessToken(registration);
        UserEntity user = userRepository.findByNormalizedPhone("+994507001005").orElseThrow();
        user.setPasswordHash("{bcrypt-sha256}changed-outside-session-flow");
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENTIALS_CHANGED"));
    }

    @Test
    void suspendedAccountInvalidatesExistingAccessToken() throws Exception {
        TestCsrfToken csrf = csrf();
        MvcResult registration = register(csrf, "0507001006");
        String accessToken = accessToken(registration);
        UserEntity user = userRepository.findByNormalizedPhone("+994507001006").orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void authenticationResponsesCannotBeCached() throws Exception {
        TestCsrfToken csrf = csrf();
        register(csrf, "0507001007")
                .getResponse();

        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    private MvcResult register(TestCsrfToken csrf, String phone) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("phone", phone)
                .put("firstName", "Sessiya")
                .put("lastName", "Testi")
                .put("password", "Secure-session-2026")
                .toString();
        return mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
    }

    private TestCsrfToken csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TestCsrfToken(cookie, body.get("csrfToken").asText());
    }

    private RefreshTokenEntity session(String phone) {
        long userId = userRepository.findByNormalizedPhone(phone).orElseThrow().getId();
        return refreshTokenRepository
                .findByUserTypeAndUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        AuthUserType.USER,
                        userId,
                        LocalDateTime.now()
                )
                .get(0);
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private Cookie refreshCookie(MvcResult result) {
        return Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .filter(cookie -> "/api/auth".equals(cookie.getPath()))
                .findFirst()
                .orElseThrow();
    }
}
