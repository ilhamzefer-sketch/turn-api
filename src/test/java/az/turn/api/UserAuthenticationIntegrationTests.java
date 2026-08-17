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
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class UserAuthenticationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuestQueueEntryRepository guestQueueEntryRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Test
    void registersLogsInAndListsCurrentSessionWithNormalizedPhone() throws Exception {
        TestCsrfToken csrf = csrf();
        MvcResult registration = register(csrf, "0501112233", "Aysel", "Məmmədova", "Safe-password-2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+994501112233"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        String accessToken = objectMapper.readTree(registration.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/api/users/me/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"+994501112233","password":"Safe-password-2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+994501112233"));
    }

    @Test
    void rejectsSecondRegistrationForActivePhone() throws Exception {
        TestCsrfToken csrf = csrf();
        register(csrf, "0501556677", "Aktiv", "İstifadəçi", "First-safe-2026")
                .andExpect(status().isOk());
        register(csrf, "+994501556677", "Başqa", "İstifadəçi", "Second-safe-2026")
                .andExpect(status().isConflict());
    }

    @Test
    void revokesEverySessionExceptTheCurrentSession() throws Exception {
        TestCsrfToken csrf = csrf();
        register(csrf, "0501667788", "Sessiya", "İstifadəçi", "Session-safe-2026")
                .andExpect(status().isOk());
        MvcResult login = login(csrf, "+994501667788", "Session-safe-2026")
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(delete("/api/users/me/sessions/others")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        MvcResult sessions = mockMvc.perform(get("/api/users/me/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(true))
                .andReturn();
        long currentSessionId = objectMapper.readTree(sessions.getResponse().getContentAsString()).get(0).get("id").asLong();

        mockMvc.perform(delete("/api/users/me/sessions/{sessionId}", currentSessionId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/me/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetRegistrationInvalidatesPreviousSessions() throws Exception {
        TestCsrfToken csrf = csrf();
        register(csrf, "0501778899", "Reset", "İstifadəçi", "Before-reset-2026")
                .andExpect(status().isOk());
        UserEntity user = userRepository.findByNormalizedPhone("+994501778899").orElseThrow();
        user.setStatus(UserStatus.PASSWORD_RESET_REQUIRED);
        user.setPasswordHash(null);
        userRepository.saveAndFlush(user);

        register(csrf, "0501778899", "Ignored", "Name", "After-reset-2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Reset"));

        long activeSessions = refreshTokenRepository
                .findByUserTypeAndUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        AuthUserType.USER,
                        user.getId(),
                        LocalDateTime.now()
                )
                .size();
        assertThat(activeSessions).isEqualTo(1);
    }

    @Test
    void completesPendingAccountWithoutChangingItsIdentityOrInvitationNames() throws Exception {
        UserEntity pending = new UserEntity();
        pending.setFirstName("Dəvət");
        pending.setLastName("Adı");
        pending.setInvitedFirstName("Dəvət");
        pending.setInvitedLastName("Adı");
        pending.setNormalizedPhone("+994502223344");
        pending.setStatus(UserStatus.PENDING);
        pending = userRepository.saveAndFlush(pending);

        TestCsrfToken csrf = csrf();
        register(csrf, "0502223344", "Leyla", "Həsənova", "Pending-safe-2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pending.getId()))
                .andExpect(jsonPath("$.firstName").value("Leyla"));

        UserEntity completed = userRepository.findById(pending.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(completed.getInvitedFirstName()).isEqualTo("Dəvət");
        assertThat(completed.getInvitedLastName()).isEqualTo("Adı");
    }

    @Test
    void locksPhoneAccountAfterFiveFailedLoginAttempts() throws Exception {
        TestCsrfToken csrf = csrf();
        register(csrf, "0503334455", "Nigar", "Əliyeva", "Correct-safe-2026")
                .andExpect(status().isOk());

        for (int attempt = 0; attempt < 4; attempt++) {
            login(csrf, "0503334455", "Wrong-safe-2026").andExpect(status().isUnauthorized());
        }
        login(csrf, "0503334455", "Wrong-safe-2026").andExpect(status().isLocked());

        UserEntity locked = userRepository.findByNormalizedPhone("+994503334455").orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void linksExistingGuestHistoryWhenPhoneOwnerRegisters() throws Exception {
        QueueEntity queue = createLegacyQueue();
        GuestQueueEntryEntity guest = new GuestQueueEntryEntity();
        guest.setQueue(queue);
        guest.setQueueNumber(1);
        guest.setFirstName("Qonaq");
        guest.setLastName("İstifadəçi");
        guest.setNormalizedPhone("+994504445566");
        guest = guestQueueEntryRepository.saveAndFlush(guest);

        TestCsrfToken csrf = csrf();
        MvcResult registration = register(csrf, "0504445566", "Qonaq", "İstifadəçi", "Guest-safe-2026")
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(registration.getResponse().getContentAsString()).get("accessToken").asText();

        GuestQueueEntryEntity linked = guestQueueEntryRepository.findById(guest.getId()).orElseThrow();
        assertThat(linked.getLinkedUser()).isNotNull();
        mockMvc.perform(get("/api/users/me/queue-history").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entrySource").value("GUEST"))
                .andExpect(jsonPath("$[0].queueNumber").value(1));
    }

    private ResultActions register(
            TestCsrfToken csrf,
            String phone,
            String firstName,
            String lastName,
            String password
    ) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("phone", phone)
                .put("firstName", firstName)
                .put("lastName", lastName)
                .put("password", password)
                .toString();
        return mockMvc.perform(post("/api/auth/register")
                .cookie(csrf.cookie())
                .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions login(
            TestCsrfToken csrf,
            String phone,
            String password
    ) throws Exception {
        String body = objectMapper.createObjectNode().put("phone", phone).put("password", password).toString();
        return mockMvc.perform(post("/api/auth/login")
                .cookie(csrf.cookie())
                .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private TestCsrfToken csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TestCsrfToken(cookie, body.get("csrfToken").asText());
    }

    private QueueEntity createLegacyQueue() {
        RegistrationEntity registration = new RegistrationEntity();
        registration.setFirstName("Legacy");
        registration.setLastName("Owner");
        registration.setEmail("legacy-" + UUID.randomUUID() + "@example.com");
        registration.setPasswordHash("hash");
        registration.setPaid(true);
        registration.setPaymentReference("LEGACY-TEST");
        registration.setRegistrationType(RegistrationType.FERDI);
        registration.setStatus(RegistrationStatus.ACTIVE);
        registration = registrationRepository.saveAndFlush(registration);

        QueueEntity queue = new QueueEntity();
        queue.setRegistration(registration);
        queue.setAddress("Bakı");
        queue.setServiceName("Test növbəsi");
        queue.setQrToken(UUID.randomUUID().toString());
        queue.setCurrentServingNumber(0);
        queue.setLastIssuedNumber(1);
        queue.setAverageServiceMinutes(5);
        queue.setResetMode(QueueResetMode.DAILY);
        queue.setActive(true);
        return queueRepository.saveAndFlush(queue);
    }
}
