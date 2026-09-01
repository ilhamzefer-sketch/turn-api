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
class AdminWalletTopUpApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private WalletAccountRepository walletAccountRepository;
    @Autowired
    private WalletTopUpRequestRepository requestRepository;
    @Autowired
    private PlatformAuditEventRepository auditRepository;

    @Test
    void adminListsReceiptAndApprovesTopUpExactlyOnce() throws Exception {
        TestCsrfToken userCsrf = csrf();
        String userToken = register(userCsrf, "0501290120");
        long requestId = createTopUp(userCsrf, userToken, "AZN_10");
        uploadReceipt(userCsrf, userToken, requestId);
        String adminToken = loginAdmin(csrf());
        TestCsrfToken adminCsrf = csrf();

        mockMvc.perform(get("/api/admin/payments/top-ups")
                        .param("status", "PENDING_REVIEW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(requestId))
                .andExpect(jsonPath("$.items[0].phone").value("+994501290120"))
                .andExpect(jsonPath("$.items[0].coinAmount").value(100))
                .andExpect(jsonPath("$.items[0].receiptAttachmentId").isNumber())
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(get("/api/admin/payments/top-ups/{id}/receipt", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE));

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/approve", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("note", "Çek yoxlanıldı").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.resolutionNote").doesNotExist());

        UserEntity user = userRepository.findByNormalizedPhone("+994501290120").orElseThrow();
        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(100);
        assertThat(auditRepository.findAll()).anySatisfy(event ->
                assertThat(event.getAction()).isEqualTo("WALLET_TOP_UP_APPROVED"));

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/approve", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void adminRejectsPendingReceiptWithReason() throws Exception {
        TestCsrfToken userCsrf = csrf();
        String userToken = register(userCsrf, "0501290121");
        long requestId = createTopUp(userCsrf, userToken, "AZN_5");
        uploadReceipt(userCsrf, userToken, requestId);
        String adminToken = loginAdmin(csrf());
        TestCsrfToken adminCsrf = csrf();

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/reject", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", "Çekdə məbləğ görünmür").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.resolutionNote").value("Çekdə məbləğ görünmür"));

        UserEntity user = userRepository.findByNormalizedPhone("+994501290121").orElseThrow();
        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isZero();
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(WalletTopUpRequestStatus.REJECTED);
    }

    private long createTopUp(TestCsrfToken csrf, String token, String packageCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("packageCode", packageCode).toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void uploadReceipt(TestCsrfToken csrf, String token, long requestId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", MediaType.IMAGE_PNG_VALUE, pngBytes());
        mockMvc.perform(multipart("/api/users/me/wallet/top-up-requests/{id}/receipt", requestId)
                        .file(file)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    private String register(TestCsrfToken csrf, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("phone", phone)
                                .put("firstName", "Payment")
                                .put("lastName", "User")
                                .put("password", "Payment-safe-2026")
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String loginAdmin(TestCsrfToken csrf) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", "admin")
                                .put("password", "NovbeTime2026!Admin")
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
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
