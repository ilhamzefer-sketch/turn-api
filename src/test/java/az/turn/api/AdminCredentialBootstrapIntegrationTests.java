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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-credential-bootstrap;DB_CLOSE_DELAY=-1",
        "app.admin.bootstrap-must-change=true",
        "app.admin.bootstrap-password-hash=$2y$10$Z5z8aWo9dvgFB/frrm30g.9LtgQmtppe0fgsH90rUWziqzEVJuequ",
        "app.security.rate-limit.auth-per-minute=100"
})
@AutoConfigureMockMvc
class AdminCredentialBootstrapIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminAccountRepository adminAccountRepository;
    @Autowired
    private PlatformAuditEventRepository auditEventRepository;
    @Autowired
    private UserPasswordService userPasswordService;

    @Test
    void bootstrapAdminMustChangeUsernameAndPasswordBeforeUsingPlatform() throws Exception {
        TestCsrfToken csrf = csrf();
        String temporaryPassword = "Admin2026!";
        String oldToken = login(csrf, "admin", temporaryPassword, true);

        mockMvc.perform(get("/api/admin/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.message").value("Davam etmək üçün ilkin admin istifadəçi adını və şifrəsini dəyişin."));

        mockMvc.perform(put("/api/admin/credentials")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("Wrong-password", "owner.admin", "SecureAdmin2026!")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/admin/credentials")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(temporaryPassword, "admin", "SecureAdmin2026!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Yeni istifadəçi adı ilkin istifadəçi adından fərqli olmalıdır."));

        MvcResult changed = mockMvc.perform(put("/api/admin/credentials")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(temporaryPassword, "owner.admin", "SecureAdmin2026!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner.admin"))
                .andExpect(jsonPath("$.mustChangeCredentials").value(false))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String newToken = objectMapper.readTree(changed.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(get("/api/admin/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());

        loginRejected(csrf(), "admin", temporaryPassword);
        login(csrf(), "owner.admin", "SecureAdmin2026!", false);
        AdminAccountEntity updated = adminAccountRepository.findByUsername("owner.admin").orElseThrow();
        assertThat(updated.isMustChangeCredentials()).isFalse();
        assertThat(updated.getCredentialsChangedAt()).isNotNull();
        assertThat(updated.getPasswordHash()).startsWith("{bcrypt-sha256}").doesNotContain("SecureAdmin2026!");
        assertThat(userPasswordService.matches("SecureAdmin2026!", updated.getPasswordHash())).isTrue();
        assertThat(auditEventRepository.findAll())
                .filteredOn(event -> "ADMIN_CREDENTIALS_CHANGED".equals(event.getAction()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getActorReference()).isEqualTo("admin");
                    assertThat(event.getDetails()).isEqualTo("username=admin->owner.admin");
                    assertThat(event.getDetails()).doesNotContain(temporaryPassword, "SecureAdmin2026!");
                });
    }

    private String login(TestCsrfToken csrf, String username, String password, boolean mustChange) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", password)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangeCredentials").value(mustChange))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void loginRejected(TestCsrfToken csrf, String username, String password) throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", password)
                                .toString()))
                .andExpect(status().isUnauthorized());
    }

    private String credentials(String currentPassword, String newUsername, String newPassword) {
        return objectMapper.createObjectNode()
                .put("currentPassword", currentPassword)
                .put("newUsername", newUsername)
                .put("newPassword", newPassword)
                .toString();
    }

    private TestCsrfToken csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TestCsrfToken(cookie, body.get("csrfToken").asText());
    }
}
