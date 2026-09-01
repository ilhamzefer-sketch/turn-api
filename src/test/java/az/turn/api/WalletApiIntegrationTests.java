package az.turn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class WalletApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletTransactionService transactionService;

    @Autowired
    private IndividualWorkspaceService workspaceService;

    @Autowired
    private AdminPlatformService adminPlatformService;

    @Test
    void registeredUserReadsBalanceAndPaginatedLedger() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0501290101");
        UserEntity user = userRepository.findByNormalizedPhone("+994501290101").orElseThrow();
        transactionService.apply(user.getId(), adminCredit(100, "api-credit-first"));
        transactionService.apply(user.getId(), adminCredit(25, "api-credit-second"));

        mockMvc.perform(get("/api/users/me/wallet")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.balance").value(125));

        mockMvc.perform(get("/api/users/me/wallet/top-up-options")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coinsPerAzn").value(10))
                .andExpect(jsonPath("$.minimumCoins").value(1))
                .andExpect(jsonPath("$.maximumCoins").value(1000000))
                .andExpect(jsonPath("$.currency").value("AZN"))
                .andExpect(jsonPath("$.whatsappUrl").value("https://wa.me/message/P63GI5XJ3PQLC1"))
                .andExpect(jsonPath("$.bankCardEnabled").value(false));

        mockMvc.perform(get("/api/users/me/wallet/transactions?page=0&size=1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].referenceKey").value("api-credit-second"))
                .andExpect(jsonPath("$.items[0].actorReference").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void rejectsUnauthenticatedAndUnboundedHistoryRequests() throws Exception {
        mockMvc.perform(get("/api/users/me/wallet"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/me/wallet/top-up-options"))
                .andExpect(status().isUnauthorized());

        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0501290102");
        mockMvc.perform(get("/api/users/me/wallet/transactions?size=101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void authenticatedUserCanOnlyReadTheirOwnWallet() throws Exception {
        TestCsrfToken firstCsrf = csrf();
        register(firstCsrf, "0501290106");
        UserEntity firstUser = userRepository.findByNormalizedPhone("+994501290106").orElseThrow();
        transactionService.apply(firstUser.getId(), adminCredit(90, "private-wallet-credit"));

        TestCsrfToken secondCsrf = csrf();
        String secondAccessToken = register(secondCsrf, "0501290107");
        UserEntity secondUser = userRepository.findByNormalizedPhone("+994501290107").orElseThrow();

        mockMvc.perform(get("/api/users/me/wallet")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(secondUser.getId()))
                .andExpect(jsonPath("$.balance").value(0));
        mockMvc.perform(get("/api/users/me/wallet/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void purchasesAnIndividualSubscriptionWithCoinsAndReplaysTheApiRequest() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0501290103");
        UserEntity user = userRepository.findByNormalizedPhone("+994501290103").orElseThrow();
        transactionService.apply(user.getId(), adminCredit(100, "api-subscription-credit"));
        IndividualWorkspaceResponseDto workspace = workspaceService.create(
                user.getId(),
                new IndividualWorkspaceCreateRequestDto("API workspace", "Asia/Baku")
        );
        String request = objectMapper.createObjectNode()
                .put("scopeType", "INDIVIDUAL_WORKSPACE")
                .put("scopeId", workspace.id())
                .put("planCode", "INDIVIDUAL_MONTHLY")
                .put("idempotencyKey", "api-individual-purchase")
                .toString();

        MvcResult first = mockMvc.perform(post("/api/subscriptions/purchase")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coinsSpent").value(30))
                .andExpect(jsonPath("$.balanceAfter").value(70))
                .andExpect(jsonPath("$.subscription.status").value("ACTIVE"))
                .andReturn();

        long paymentId = objectMapper.readTree(first.getResponse().getContentAsString()).get("paymentId").asLong();
        mockMvc.perform(post("/api/subscriptions/purchase")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.balanceAfter").value(70));

        mockMvc.perform(post("/api/subscriptions/checkout")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("scopeType", "INDIVIDUAL_WORKSPACE")
                                .put("scopeId", workspace.id())
                                .put("planCode", "INDIVIDUAL_MONTHLY")
                                .toString()))
                .andExpect(status().isGone());

        assertThat(adminPlatformService.overview().completedSubscriptionPayments()).isPositive();
    }

    @Test
    void retiresEveryLegacySubscriptionBankRoute() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0501290105");

        mockMvc.perform(post("/api/subscriptions/checkout")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isGone());

        mockMvc.perform(get("/api/subscriptions/payments/99")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isGone());

        mockMvc.perform(post("/api/subscriptions/payments/99/confirm")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isGone());

        mockMvc.perform(post("/api/subscriptions/payments/99/cancel")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isGone());
    }

    @Test
    void rejectsUnauthenticatedAndInvalidSubscriptionPurchases() throws Exception {
        TestCsrfToken unauthenticatedCsrf = csrf();
        String validBody = objectMapper.createObjectNode()
                .put("scopeType", "INDIVIDUAL_WORKSPACE")
                .put("scopeId", 1)
                .put("planCode", "INDIVIDUAL_MONTHLY")
                .put("idempotencyKey", "unauthenticated-purchase")
                .toString();
        mockMvc.perform(post("/api/subscriptions/purchase")
                        .cookie(unauthenticatedCsrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, unauthenticatedCsrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());

        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0501290104");
        mockMvc.perform(post("/api/subscriptions/purchase")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("scopeType", "INDIVIDUAL_WORKSPACE")
                                .put("scopeId", 1)
                                .put("planCode", "INDIVIDUAL_MONTHLY")
                                .put("idempotencyKey", "")
                                .toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createsFixedTopUpRequestAndBlocksAnotherActiveRequest() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0501290110");
        String body = objectMapper.createObjectNode().put("packageCode", "AZN_10").toString();

        mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageCode").value("AZN_10"))
                .andExpect(jsonPath("$.amountAzn").value(10))
                .andExpect(jsonPath("$.coinAmount").value(100))
                .andExpect(jsonPath("$.paymentUrl").value("https://cb.birbank.business/pay/75c998cbda8e4674bb11cbf961d91c27"))
                .andExpect(jsonPath("$.status").value("AWAITING_RECEIPT"))
                .andExpect(jsonPath("$.receiptUploadOpen").value(true));

        mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("packageCode", "AZN_5").toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOP_UP_ACTIVE_REQUEST_EXISTS"));

        mockMvc.perform(get("/api/users/me/wallet/top-up-requests/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coinAmount").value(100));
    }

    @Test
    void uploadsReceiptAndMovesRequestToPendingReview() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0501290111");
        MvcResult created = mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("packageCode", "AZN_3").toString()))
                .andExpect(status().isOk())
                .andReturn();
        long requestId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MockMultipartFile receipt = new MockMultipartFile(
                "file", "kapital-receipt.png", MediaType.IMAGE_PNG_VALUE, pngBytes()
        );
        mockMvc.perform(multipart("/api/users/me/wallet/top-up-requests/{id}/receipt", requestId)
                        .file(receipt)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.receiptUploadedAt").exists())
                .andExpect(jsonPath("$.receiptUploadOpen").value(false));

        assertThat(walletTopUpRequestRepository.findById(requestId).orElseThrow().getReceiptAttachment()).isNotNull();
    }

    @Autowired
    private WalletTopUpRequestRepository walletTopUpRequestRepository;

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private String register(TestCsrfToken csrf, String phone) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("phone", phone)
                .put("firstName", "Wallet")
                .put("lastName", "API")
                .put("password", "Wallet-safe-2026")
                .toString();
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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

    private WalletTransactionCommandDto adminCredit(long amount, String reference) {
        return new WalletTransactionCommandDto(
                WalletTransactionType.ADMIN_CREDIT,
                amount,
                WalletActorType.ADMIN,
                null,
                "bootstrap-admin",
                reference,
                "Manual API test credit"
        );
    }
}
