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
import java.util.UUID;

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
class EpointCheckoutRegressionTests {
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
        epointServer.createContext("/api/1/payment-request", EpointCheckoutRegressionTests::checkout);
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
    void successfulCallbackAfterReplacementMustStillCreditOriginalPayment() throws Exception {
        TestCsrfToken csrf = csrf();
        String token = register(csrf, "0501290191");
        long first = createRequest(csrf, token);
        WalletTopUpRequestEntity original = topUpRequestRepository.findById(first).orElseThrow();
        mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                .cookie(csrf.cookie()).header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"packageCode\":\"AZN_5\"}"))
                .andExpect(status().isOk());
        assertThat(topUpRequestRepository.findById(first).orElseThrow().getStatus())
                .isEqualTo(WalletTopUpRequestStatus.SUPERSEDED);
        mockMvc.perform(post("/api/payments/epoint/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(signedForm(Map.of("order_id", original.getExternalOrderId(), "status", "success", "amount", "10.00", "currency", "AZN", "transaction", original.getExternalCheckoutTransactionId()))))
                .andExpect(status().isOk());
        assertThat(topUpRequestRepository.findById(first).orElseThrow().getStatus()).isEqualTo(WalletTopUpRequestStatus.PAID);
    }

    @Test
    void activeExternalCheckoutMustRemainVisibleBeforeDeadline() throws Exception {
        TestCsrfToken csrf = csrf();
        String token = register(csrf, "0501290192");
        long id = createRequest(csrf, token);
        mockMvc.perform(get("/api/users/me/wallet/top-up-requests/active")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    private long createRequest(TestCsrfToken csrf, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/me/wallet/top-up-requests")
                .cookie(csrf.cookie()).header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.createObjectNode().put("packageCode", "AZN_10").toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private static void checkout(HttpExchange exchange) throws IOException {
        byte[] body = """
                {"status":"success","redirect_url":"https://epoint.test/checkout","transaction":"%s"}
                """.formatted(UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
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
