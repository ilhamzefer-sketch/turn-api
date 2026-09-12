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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
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
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    @Test
    void groupedFiltersAndGlobalSummaryRespectBakuDayBoundary() throws Exception {
        String adminToken = loginAdmin(csrf());
        TestCsrfToken adminCsrf = csrf();
        JsonNode before = listTopUps(adminToken, "", 0, 1).get("summary");

        TestCsrfToken paidCsrf = csrf();
        String paidToken = register(paidCsrf, "0501290140");
        UserEntity paidUser = userRepository.findByNormalizedPhone("+994501290140").orElseThrow();
        paidUser.setConfirmedWalletFraudCount(3);
        userRepository.saveAndFlush(paidUser);
        long paidId = createTopUp(paidCsrf, paidToken, "AZN_5");
        uploadReceipt(paidCsrf, paidToken, paidId);
        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/approve", paidId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        TestCsrfToken failedCsrf = csrf();
        String failedToken = register(failedCsrf, "0501290141");
        UserEntity failedUser = userRepository.findByNormalizedPhone("+994501290141").orElseThrow();
        failedUser.setConfirmedWalletFraudCount(3);
        userRepository.saveAndFlush(failedUser);
        long failedId = createTopUp(failedCsrf, failedToken, "AZN_3");
        uploadReceipt(failedCsrf, failedToken, failedId);
        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/reject", failedId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Çek uyğun deyil\"}"))
                .andExpect(status().isOk());

        TestCsrfToken waitingCsrf = csrf();
        String waitingToken = register(waitingCsrf, "0501290142");
        long waitingId = createTopUp(waitingCsrf, waitingToken, "AZN_10");

        assertThat(containsId(listTopUps(adminToken, "PAID_GROUP", 0, 100), paidId)).isTrue();
        assertThat(containsId(listTopUps(adminToken, "FAILED_GROUP", 0, 100), failedId)).isTrue();
        assertThat(containsId(listTopUps(adminToken, "WAITING_GROUP", 0, 100), waitingId)).isTrue();

        JsonNode page = listTopUps(adminToken, "PAID_GROUP", 0, 1);
        assertThat(page.get("hasNext").asBoolean()).isTrue();
        JsonNode nextPage = listTopUps(adminToken, "PAID_GROUP", 1, 1);
        assertThat(nextPage.get("items").get(0).get("id").asLong())
                .isNotEqualTo(page.get("items").get(0).get("id").asLong());
        JsonNode after = page.get("summary");
        assertThat(after.get("total").asLong()).isEqualTo(before.get("total").asLong() + 3);
        assertThat(after.get("paid").asLong()).isEqualTo(before.get("paid").asLong() + 1);
        assertThat(after.get("failed").asLong()).isEqualTo(before.get("failed").asLong() + 1);
        assertThat(after.get("waiting").asLong()).isEqualTo(before.get("waiting").asLong() + 1);
        assertThat(after.get("timezone").asText()).isEqualTo("Asia/Baku");
        BigDecimal paidTodayBefore = before.get("paidTodayAmount").decimalValue();
        assertThat(after.get("paidTodayAmount").decimalValue()).isEqualByComparingTo(paidTodayBefore.add(new BigDecimal("5")));

        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Baku")).minusDays(1);
        LocalDateTime yesterdayAt23 = inStorageZone(yesterday, 23, 0, 0);
        LocalDateTime yesterdayAt2330 = inStorageZone(yesterday, 23, 30, 0);
        LocalDateTime yesterdayAt2310 = inStorageZone(yesterday, 23, 10, 0);
        LocalDateTime yesterdayAt2359 = inStorageZone(yesterday, 23, 59, 59);
        jdbcTemplate.update("update wallet_top_up_requests set created_at = ?, clicked_at = ?, "
                        + "receipt_deadline_at = ?, receipt_uploaded_at = ?, reviewed_at = ?, updated_at = ? where id = ?",
                Timestamp.valueOf(yesterdayAt23),
                Timestamp.valueOf(yesterdayAt23),
                Timestamp.valueOf(yesterdayAt2330),
                Timestamp.valueOf(yesterdayAt2310),
                Timestamp.valueOf(yesterdayAt2359),
                Timestamp.valueOf(yesterdayAt2359), paidId);
        assertThat(listTopUps(adminToken, "WAITING_GROUP", 0, 1).get("summary")
                .get("paidTodayAmount").decimalValue()).isEqualByComparingTo(paidTodayBefore);
    }

    private LocalDateTime inStorageZone(LocalDate day, int hour, int minute, int second) {
        return LocalDateTime.ofInstant(
                day.atTime(hour, minute, second).atZone(ZoneId.of("Asia/Baku")).toInstant(),
                clock.getZone()
        );
    }

    private JsonNode listTopUps(String adminToken, String statusFilter, int page, int size) throws Exception {
        var request = get("/api/admin/payments/top-ups")
                .param("page", Integer.toString(page))
                .param("size", Integer.toString(size))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        if (!statusFilter.isBlank()) request.param("status", statusFilter);
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private boolean containsId(JsonNode page, long id) {
        return page.get("items").valueStream().anyMatch(item -> item.get("id").asLong() == id);
    }

    @Test
    void adminListsReceiptAndApprovesTopUpExactlyOnce() throws Exception {
        TestCsrfToken userCsrf = csrf();
        String userToken = register(userCsrf, "0501290120");
        long requestId = createTopUp(userCsrf, userToken, "AZN_10");
        uploadReceipt(userCsrf, userToken, requestId);
        String adminToken = loginAdmin(csrf());
        TestCsrfToken adminCsrf = csrf();
        UserEntity user = userRepository.findByNormalizedPhone("+994501290120").orElseThrow();
        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(100);

        MvcResult listResult = mockMvc.perform(get("/api/admin/payments/top-ups")
                        .param("status", "REVIEW_REQUIRED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listedRequest = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("items")
                .valueStream()
                .filter(item -> item.get("id").asLong() == requestId)
                .findFirst()
                .orElseThrow();
        assertThat(listedRequest.get("phone").asText()).isEqualTo("+994501290120");
        assertThat(listedRequest.get("coinAmount").asLong()).isEqualTo(100);
        assertThat(listedRequest.get("status").asText()).isEqualTo("AUTO_CREDITED_PENDING_REVIEW");
        assertThat(listedRequest.get("receiptAttachmentId").isNumber()).isTrue();

        mockMvc.perform(get("/api/admin/payments/top-ups/{id}/receipt", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE));

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/reject", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", "Uyğun deyil").toString()))
                .andExpect(status().isConflict());
        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(100);

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/approve", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("note", "Çek yoxlanıldı").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.resolutionNote").doesNotExist());

        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(100);
        assertThat(auditRepository.findAll()).anySatisfy(event ->
                assertThat(event.getAction()).isEqualTo("WALLET_TOP_UP_VERIFIED"));

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/approve", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminApprovalCreditsAUserWhoseReceiptRequiresManualReview() throws Exception {
        TestCsrfToken userCsrf = csrf();
        String userToken = register(userCsrf, "0501290123");
        UserEntity user = userRepository.findByNormalizedPhone("+994501290123").orElseThrow();
        user.setConfirmedWalletFraudCount(3);
        userRepository.saveAndFlush(user);
        long requestId = createTopUp(userCsrf, userToken, "AZN_5");
        uploadReceipt(userCsrf, userToken, requestId);
        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isZero();
        String adminToken = loginAdmin(csrf());
        TestCsrfToken adminCsrf = csrf();

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/approve", requestId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("note", "Ödəniş təsdiqləndi").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(50);
    }

    @Test
    void downloadsPdfReceiptsWithoutInlineExecution() throws Exception {
        TestCsrfToken userCsrf = csrf();
        String userToken = register(userCsrf, "0501290122");
        long requestId = createTopUp(userCsrf, userToken, "AZN_3");
        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.pdf", MediaType.APPLICATION_PDF_VALUE, SecurePdfTestFiles.onePage()
        );
        mockMvc.perform(multipart("/api/users/me/wallet/top-up-requests/{id}/receipt", requestId)
                        .file(file)
                        .cookie(userCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, userCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk());

        String adminToken = loginAdmin(csrf());
        mockMvc.perform(get("/api/admin/payments/top-ups/{id}/receipt", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment;")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void adminRejectsPendingReceiptWithReason() throws Exception {
        TestCsrfToken userCsrf = csrf();
        String userToken = register(userCsrf, "0501290121");
        UserEntity user = userRepository.findByNormalizedPhone("+994501290121").orElseThrow();
        user.setConfirmedWalletFraudCount(3);
        userRepository.saveAndFlush(user);
        long requestId = createTopUp(userCsrf, userToken, "AZN_5");
        uploadReceipt(userCsrf, userToken, requestId);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(WalletTopUpRequestStatus.MANUAL_REVIEW);
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
                .andExpect(status().isOk());
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
