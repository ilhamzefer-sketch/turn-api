package az.turn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.rate-limit.auth-per-minute=100",
        "app.payment.epoint.public-key=i000000001",
        "app.payment.epoint.private-key=sandbox_private_key_0000000001",
        "app.payment.epoint.success-url=http://127.0.0.1:5276/app/wallet?payment=success",
        "app.payment.epoint.error-url=http://127.0.0.1:5276/app/wallet?payment=failed",
        "app.payment.epoint.result-url=http://127.0.0.1:8082/api/payments/epoint/callback"
})
@AutoConfigureMockMvc
class EpointWalletTopUpIntegrationTests {
    private static final String PRIVATE_KEY = "sandbox_private_key_0000000001";
    private static HttpServer epointServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletTopUpRequestRepository topUpRequestRepository;

    @Autowired
    private WalletAccountRepository walletAccountRepository;

    @DynamicPropertySource
    static void epointProperties(DynamicPropertyRegistry registry) throws IOException {
        epointServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        epointServer.createContext("/api/1/request", EpointWalletTopUpIntegrationTests::checkout);
        epointServer.start();
        registry.add(
                "app.payment.epoint.api-base-url",
                () -> "http://127.0.0.1:" + epointServer.getAddress().getPort() + "/api/1"
        );
    }

    @AfterAll
    static void stopServer() {
        if (epointServer != null) {
            epointServer.stop(0);
        }
    }

    @Test
    void successfulEpointCallbackCreditsWalletAndReleasesActiveTopUp() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0501290124");

        MvcResult created = mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("packageCode", "AZN_3").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentUrl").value("http://epoint.test/checkout"))
                .andReturn();

        long requestId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        WalletTopUpRequestEntity request = topUpRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getExternalOrderId()).startsWith("wallet-" + requestId + "-");

        mockMvc.perform(post("/api/payments/epoint/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(signedForm(Map.of(
                                "order_id", request.getExternalOrderId(),
                                "status", "success",
                                "amount", "3.00",
                                "transaction", "EPOINT-TEST-1"
                        ))))
                .andExpect(status().isOk());

        WalletTopUpRequestEntity paid = topUpRequestRepository.findById(requestId).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(WalletTopUpRequestStatus.PAID);
        assertThat(paid.getActiveUserId()).isNull();
        assertThat(paid.getWalletTransaction()).isNotNull();
        assertThat(walletAccountRepository.findByUserId(paid.getUser().getId()).orElseThrow().getBalance()).isEqualTo(30);

        mockMvc.perform(get("/api/users/me/wallet")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(30));
    }

    @Test
    void newEpointTopUpReplacesUnfinishedExternalCheckout() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0501290125");

        MvcResult first = mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("packageCode", "AZN_10").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_RECEIPT"))
                .andReturn();

        long firstRequestId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("packageCode", "AZN_3").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageCode").value("AZN_3"))
                .andExpect(jsonPath("$.status").value("AWAITING_RECEIPT"));

        WalletTopUpRequestEntity replaced = topUpRequestRepository.findById(firstRequestId).orElseThrow();
        assertThat(replaced.getStatus()).isEqualTo(WalletTopUpRequestStatus.PAYMENT_FAILED);
        assertThat(replaced.getExternalPaymentStatus()).isEqualTo("replaced_by_new_request");
        assertThat(replaced.getActiveUserId()).isNull();
    }

    private static void checkout(HttpExchange exchange) throws IOException {
        byte[] body = """
                {"status":"success","redirect_url":"http://epoint.test/checkout","transaction":"EPOINT-CHECKOUT-1"}
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String signedForm(Map<String, Object> payload) throws Exception {
        String data = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(new LinkedHashMap<>(payload)));
        String signature = EpointSignature.sign(data, PRIVATE_KEY);
        return "data=" + URLEncoder.encode(data, StandardCharsets.UTF_8)
                + "&signature=" + URLEncoder.encode(signature, StandardCharsets.UTF_8);
    }

    private String register(TestCsrfToken csrf, String phone) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("phone", phone)
                .put("firstName", "Wallet")
                .put("lastName", "Epoint")
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
}
