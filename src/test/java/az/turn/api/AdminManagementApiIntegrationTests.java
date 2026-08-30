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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class AdminManagementApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletAccountRepository walletAccountRepository;
    @Autowired
    private PlatformAuditEventRepository auditEventRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private SubscriptionPlanRepository planRepository;
    @Autowired
    private ProviderSubscriptionRepository subscriptionRepository;
    @Autowired
    private SubscriptionActivationService activationService;

    @Test
    void defaultAdminListsUsersAndCreditsCoinsIdempotently() throws Exception {
        TestCsrfToken registrationCsrf = csrf();
        String userToken = register(registrationCsrf, "0501390101");
        UserEntity user = userRepository.findByNormalizedPhone("+994501390101").orElseThrow();
        String adminToken = loginAdmin(csrf(), "admin", "NovbeTime2026!Admin");
        TestCsrfToken mutationCsrf = csrf();
        String credit = objectMapper.createObjectNode()
                .put("amount", 75)
                .put("reason", "Müştəri balansının manual artırılması")
                .put("idempotencyKey", "admin-api-credit-1390101")
                .toString();

        mockMvc.perform(get("/api/admin/users")
                        .param("search", "0501390101")
                        .param("page", "0")
                        .param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(user.getId()))
                .andExpect(jsonPath("$.items[0].coinBalance").value(0));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/admin/users/{userId}/coins", user.getId())
                            .cookie(mutationCsrf.cookie())
                            .header(CsrfCookieFilter.CSRF_HEADER_NAME, mutationCsrf.value())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credit))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balanceAfter").value(75));
        }

        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(75);
        assertThat(auditEventRepository.findAll()).anySatisfy(event -> {
            assertThat(event.getAction()).isEqualTo("USER_COINS_CREDITED");
            assertThat(event.getTargetType()).isEqualTo("USER");
            assertThat(event.getTargetId()).isEqualTo(user.getId());
            assertThat(event.getActorReference()).isEqualTo("admin");
            assertThat(event.getDetails()).contains("coins=75", "admin-credit:admin:admin-api-credit-1390101");
        });
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesAnotherAdminWhoCanLogIn() throws Exception {
        String adminToken = loginAdmin(csrf(), "admin", "NovbeTime2026!Admin");
        TestCsrfToken mutationCsrf = csrf();
        String username = "operations.13902";
        String body = objectMapper.createObjectNode()
                .put("username", username)
                .put("displayName", "Əməliyyat administratoru")
                .put("password", "Operations-safe-2026")
                .toString();

        mockMvc.perform(post("/api/admin/admins")
                        .cookie(mutationCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, mutationCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/admin/admins/")))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        loginAdmin(csrf(), username, "Operations-safe-2026");
        mockMvc.perform(post("/api/admin/admins")
                        .cookie(mutationCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, mutationCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void adminIncreasesBusinessRoomLimitAndRenewalKeepsOverride() throws Exception {
        UserEntity owner = createOwner("+994501390103");
        BusinessEntity business = createBusiness(owner);
        SubscriptionPlanEntity plan = planRepository.findByCodeAndActiveTrue("BUSINESS_MONTHLY").orElseThrow();
        ProviderSubscriptionEntity subscription = createSubscription(business, plan);
        String adminToken = loginAdmin(csrf(), "admin", "NovbeTime2026!Admin");
        TestCsrfToken mutationCsrf = csrf();

        mockMvc.perform(put("/api/admin/businesses/{businessId}/room-limit", business.getId())
                        .cookie(mutationCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, mutationCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("roomLimit", 8)
                                .put("reason", "Müştərinin əlavə otaq müraciəti təsdiqləndi")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomLimit").value(8));

        assertThat(subscriptionRepository.findById(subscription.getId()).orElseThrow().getRoomLimit()).isEqualTo(8);
        activationService.activate(subscription.getId(), plan);
        assertThat(subscriptionRepository.findById(subscription.getId()).orElseThrow().getRoomLimit()).isEqualTo(8);
        mockMvc.perform(get("/api/admin/businesses")
                        .param("search", "Admin Business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roomLimit").value(8));
    }

    @Test
    void rejectsMissingAuthenticationInvalidPagingAndInvalidCredit() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        String adminToken = loginAdmin(csrf(), "admin", "NovbeTime2026!Admin");
        mockMvc.perform(get("/api/admin/users?size=51")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        TestCsrfToken mutationCsrf = csrf();
        mockMvc.perform(post("/api/admin/users/999999/coins")
                        .cookie(mutationCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, mutationCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("amount", 0)
                                .put("reason", "x")
                                .put("idempotencyKey", "short")
                                .toString()))
                .andExpect(status().isBadRequest());
    }

    private String register(TestCsrfToken csrf, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("phone", phone)
                                .put("firstName", "Admin")
                                .put("lastName", "User")
                                .put("password", "User-safe-2026")
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String loginAdmin(TestCsrfToken csrf, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", password)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private TestCsrfToken csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TestCsrfToken(cookie, body.get("csrfToken").asText());
    }

    private UserEntity createOwner(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Business");
        user.setLastName("Owner");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private BusinessEntity createBusiness(UserEntity owner) {
        BusinessEntity business = new BusinessEntity();
        business.setPrimaryOwnerUser(owner);
        business.setName("Admin Business");
        business.setNormalizedPhone(owner.getNormalizedPhone());
        business.setTimezone("Asia/Baku");
        business.setStatus(ProviderStatus.ACTIVE);
        return businessRepository.saveAndFlush(business);
    }

    private ProviderSubscriptionEntity createSubscription(BusinessEntity business, SubscriptionPlanEntity plan) {
        LocalDateTime now = LocalDateTime.now();
        ProviderSubscriptionEntity subscription = new ProviderSubscriptionEntity();
        subscription.setScopeType(ProviderScopeType.BUSINESS);
        subscription.setScopeId(business.getId());
        subscription.setPlan(plan);
        subscription.setBillingPeriod(BillingPeriod.MONTHLY);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setRoomLimit(5);
        subscription.setEmployeeLimit(500);
        subscription.setStartsAt(now);
        subscription.setExpiresAt(now.plusMonths(1));
        subscription.setGraceEndsAt(now.plusMonths(1).plusDays(7));
        return subscriptionRepository.saveAndFlush(subscription);
    }
}
