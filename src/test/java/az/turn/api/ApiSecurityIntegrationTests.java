package az.turn.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=2")
@AutoConfigureMockMvc
class ApiSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PaymentSessionRepository paymentSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void rejectsInvalidRegistrationBeforeCallingBank() throws Exception {
        Csrf csrf = csrf();
        mockMvc.perform(post("/api/payments/registration-sessions")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"A","lastName":"1","email":"not-an-email","password":"short","registrationType":"FERDI"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void requiresPaymentSessionToken() throws Exception {
        mockMvc.perform(get("/api/payments/registration-sessions/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsUnauthorizedInsteadOfForbiddenForQueueCreationWithoutJwt() throws Exception {
        Csrf csrf = csrf();
        mockMvc.perform(post("/api/queues")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rateLimitsAuthenticationEndpoints() throws Exception {
        Csrf csrf = csrf();
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/login")
                            .cookie(csrf.cookie())
                            .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@example.com\",\"password\":\"Password1\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/login")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"Password1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void completedPaymentCanIssueAutomaticLoginOnlyOnce() throws Exception {
        RegistrationEntity registration = new RegistrationEntity();
        registration.setFirstName("Test");
        registration.setLastName("User");
        registration.setEmail("replay-test@example.com");
        registration.setPasswordHash(passwordEncoder.encode("Password1"));
        registration.setPaid(true);
        registration.setPaymentReference("KAPITAL-ORDER-REPLAY");
        registration.setRegistrationType(RegistrationType.FERDI);
        registration.setStatus(RegistrationStatus.ACTIVE);
        registration = registrationRepository.save(registration);

        PaymentSessionEntity session = new PaymentSessionEntity();
        session.setSessionToken("replay-session-token");
        session.setProvider("birbank");
        session.setPaymentMode("test");
        session.setStatus(PaymentStatus.COMPLETED);
        session.setRegistrationType(RegistrationType.FERDI);
        session.setAmount(20);
        session.setCurrency("AZN");
        session.setFirstName("Test");
        session.setLastName("User");
        session.setEmail("replay-test@example.com");
        session.setPasswordHash(passwordEncoder.encode("Password1"));
        session.setRegistration(registration);
        session.setCardHolder("Test User");
        session.setCardLast4("0000");
        session.setSandboxOutcome("SUCCESS");
        session.setPaymentReference("KAPITAL-ORDER-REPLAY");
        session = paymentSessionRepository.save(session);

        Csrf csrf = csrf();
        var request = post("/api/payments/registration-sessions/{id}/confirm", session.getId())
                .cookie(csrf.cookie(), new Cookie("payment_session_token", session.getSessionToken()))
                .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.payment.sessionToken").doesNotExist())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("payment_session_token=;"))));
        mockMvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.registration").doesNotExist());
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        if (cookie == null) throw new IllegalStateException("CSRF cookie yaradılmadı");
        String body = result.getResponse().getContentAsString();
        String token = body.substring(body.indexOf(':') + 2, body.length() - 2);
        return new Csrf(cookie, token);
    }

    private record Csrf(Cookie cookie, String value) {}
}
