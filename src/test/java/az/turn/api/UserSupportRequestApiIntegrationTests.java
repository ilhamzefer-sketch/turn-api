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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class UserSupportRequestApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformAuditEventRepository auditRepository;

    @Test
    void userCreatesProblemUploadsImageAndAdminReviewsIt() throws Exception {
        TestCsrfToken userCsrf = csrf();
        String userToken = register(userCsrf, "0501290301");
        long requestId = create(userCsrf, userToken, "PROBLEM", "Növbə səhifəsi açılmır.");

        mockMvc.perform(multipart("/api/users/me/support-requests/{id}/attachment", requestId)
                        .file(new MockMultipartFile("file", "screen.png", MediaType.IMAGE_PNG_VALUE, pngBytes()))
                        .cookie(userCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, userCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAttachment").value(true))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/users/me/support-requests/{id}/attachment", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE));

        String adminToken = loginAdmin(csrf());
        TestCsrfToken adminCsrf = csrf();
        mockMvc.perform(get("/api/admin/support-requests")
                        .param("requestType", "PROBLEM")
                        .param("status", "OPEN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(requestId))
                .andExpect(jsonPath("$.items[0].phone").value("+994501290301"))
                .andExpect(jsonPath("$.items[0].attachmentId").isNumber());

        mockMvc.perform(get("/api/admin/support-requests/{id}/attachment", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE));

        mockMvc.perform(post("/api/admin/support-requests/{id}/review", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("status", "RESOLVED")
                                .put("response", "Problem həll edildi.").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.adminResponse").value("Problem həll edildi."));

        mockMvc.perform(post("/api/admin/support-requests/{id}/review", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("status", "REJECTED")
                                .put("response", "Təkrar yoxlama tələb olunur.").toString()))
                .andExpect(status().isConflict());

        assertThat(auditRepository.findAll()).anySatisfy(event ->
                assertThat(event.getAction()).isEqualTo("SUPPORT_REQUEST_REVIEWED"));
    }

    @Test
    void userCannotReadAnotherUsersRequestAndTerminalReviewNeedsResponse() throws Exception {
        TestCsrfToken firstCsrf = csrf();
        String firstToken = register(firstCsrf, "0501290302");
        long requestId = create(firstCsrf, firstToken, "SUGGESTION", "Mobil tətbiq əlavə olunsun.");
        TestCsrfToken secondCsrf = csrf();
        String secondToken = register(secondCsrf, "0501290303");

        mockMvc.perform(get("/api/users/me/support-requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isNotFound());

        String adminToken = loginAdmin(csrf());
        TestCsrfToken adminCsrf = csrf();
        mockMvc.perform(post("/api/admin/support-requests/{id}/review", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("status", "RESOLVED").toString()))
                .andExpect(status().isConflict());
    }

    private long create(TestCsrfToken csrf, String token, String type, String message) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/me/support-requests")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("requestType", type).put("message", message).toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String register(TestCsrfToken csrf, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("phone", phone).put("firstName", "Support")
                                .put("lastName", "User").put("password", "Support-safe-2026").toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String loginAdmin(TestCsrfToken csrf) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                        .cookie(csrf.cookie()).header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("username", "admin")
                                .put("password", "NovbeTime2026!Admin").toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private TestCsrfToken csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TestCsrfToken(cookie, body.get("csrfToken").asText());
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
