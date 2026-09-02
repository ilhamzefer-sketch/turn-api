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
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class AdminWalletFraudApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletAccountRepository walletAccountRepository;
    @Autowired
    private WalletTopUpRequestRepository topUpRepository;
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;
    @Autowired
    private IndividualWorkspaceService workspaceService;
    @Autowired
    private SubscriptionCoinPaymentService coinPaymentService;
    @Autowired
    private SubscriptionCoinPaymentRepository coinPaymentRepository;
    @Autowired
    private ProviderSubscriptionRepository subscriptionRepository;
    @Autowired
    private PlatformAuditEventRepository auditRepository;

    @Test
    void confirmsFraudCancelsAffectedSubscriptionAndReversesCoinsExactlyOnce() throws Exception {
        TestCsrfToken userCsrf = csrf();
        String userToken = register(userCsrf, "0501290130");
        UserEntity user = userRepository.findByNormalizedPhone("+994501290130").orElseThrow();
        long topUpId = createAndUploadTopUp(userCsrf, userToken, "AZN_3");
        IndividualWorkspaceResponseDto workspace = workspaceService.create(
                user.getId(),
                new IndividualWorkspaceCreateRequestDto("Fırıldaq testi", "Asia/Baku")
        );
        SubscriptionCoinPurchaseDto purchase = coinPaymentService.purchase(
                user.getId(),
                new SubscriptionCoinPurchaseRequestDto(
                        ProviderScopeType.INDIVIDUAL_WORKSPACE,
                        workspace.id(),
                        "INDIVIDUAL_MONTHLY",
                        "fraud-subscription-purchase"
                )
        );
        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isZero();
        String adminToken = loginAdmin(csrf());
        TestCsrfToken adminCsrf = csrf();

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/confirm-fraud", topUpId)
                        .cookie(userCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, userCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", "Saxta çek").toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/confirm-fraud", topUpId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", " ").toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/confirm-fraud", topUpId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("reason", "Bank hesabına ödəniş daxil olmayıb")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FRAUD_CONFIRMED"))
                .andExpect(jsonPath("$.confirmedFraudCount").value(1))
                .andExpect(jsonPath("$.fraudCountAfter").value(1));

        WalletTopUpRequestEntity topUp = topUpRepository.findById(topUpId).orElseThrow();
        SubscriptionCoinPaymentEntity payment = coinPaymentRepository.findById(purchase.paymentId()).orElseThrow();
        ProviderSubscriptionEntity subscription = subscriptionRepository.findById(
                purchase.subscription().id()
        ).orElseThrow();
        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isZero();
        WalletTransactionEntity reversal = walletTransactionRepository.findById(
                topUp.getReversalWalletTransaction().getId()
        ).orElseThrow();
        WalletTransactionEntity refund = walletTransactionRepository.findById(
                payment.getRefundWalletTransaction().getId()
        ).orElseThrow();
        assertThat(reversal.getType()).isEqualTo(WalletTransactionType.TOP_UP_REVERSAL);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(refund.getType()).isEqualTo(WalletTransactionType.REFUND);
        assertThat(payment.getFraudTopUpRequest().getId()).isEqualTo(topUpId);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getConfirmedWalletFraudCount()).isEqualTo(1);
        assertThat(auditRepository.findAll()).anySatisfy(event ->
                assertThat(event.getAction()).isEqualTo("WALLET_TOP_UP_FRAUD_CONFIRMED"));

        mockMvc.perform(post("/api/admin/payments/top-ups/{id}/confirm-fraud", topUpId)
                        .cookie(adminCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, adminCsrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", "Təkrar sorğu").toString()))
                .andExpect(status().isConflict());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getConfirmedWalletFraudCount()).isEqualTo(1);
    }

    @Test
    void thirdFraudMovesFutureReceiptsToManualReviewAndManualFraudStillCounts() throws Exception {
        TestCsrfToken userCsrf = csrf();
        String userToken = register(userCsrf, "0501290131");
        UserEntity user = userRepository.findByNormalizedPhone("+994501290131").orElseThrow();
        user.setConfirmedWalletFraudCount(2);
        userRepository.saveAndFlush(user);
        long automaticTopUpId = createAndUploadTopUp(userCsrf, userToken, "AZN_3");
        String adminToken = loginAdmin(csrf());
        TestCsrfToken adminCsrf = csrf();

        confirmFraud(adminCsrf, adminToken, automaticTopUpId, "Üçüncü saxta çek")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fraudCountAfter").value(3));
        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isZero();

        long manualTopUpId = createAndUploadTopUp(userCsrf, userToken, "AZN_5");
        WalletTopUpRequestEntity manual = topUpRepository.findById(manualTopUpId).orElseThrow();
        assertThat(manual.getStatus()).isEqualTo(WalletTopUpRequestStatus.MANUAL_REVIEW);
        assertThat(manual.getWalletTransaction()).isNull();

        confirmFraud(adminCsrf, adminToken, manualTopUpId, "Dördüncü saxta çek")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fraudCountAfter").value(4));
        WalletTopUpRequestEntity confirmedManual = topUpRepository.findById(manualTopUpId).orElseThrow();
        assertThat(confirmedManual.getReversalWalletTransaction()).isNull();
        assertThat(walletAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isZero();
    }

    private ResultActions confirmFraud(
            TestCsrfToken csrf,
            String token,
            long topUpId,
            String reason
    ) throws Exception {
        return mockMvc.perform(post("/api/admin/payments/top-ups/{id}/confirm-fraud", topUpId)
                .cookie(csrf.cookie())
                .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.createObjectNode().put("reason", reason).toString()));
    }

    private long createAndUploadTopUp(TestCsrfToken csrf, String token, String packageCode) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("packageCode", packageCode).toString()))
                .andExpect(status().isOk())
                .andReturn();
        long requestId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        MockMultipartFile receipt = new MockMultipartFile(
                "file", "receipt.png", MediaType.IMAGE_PNG_VALUE, pngBytes()
        );
        mockMvc.perform(multipart("/api/users/me/wallet/top-up-requests/{id}/receipt", requestId)
                        .file(receipt)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        return requestId;
    }

    private String register(TestCsrfToken csrf, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("phone", phone)
                                .put("firstName", "Fraud")
                                .put("lastName", "Test")
                                .put("password", "Fraud-safe-2026")
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
