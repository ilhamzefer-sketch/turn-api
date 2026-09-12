package az.turn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class WalletPaymentTestSupport {
    static final String PRIVATE_KEY = "local-test-private-key";
    private static final AtomicLong PHONES = new AtomicLong(509400000);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired WalletTopUpRequestService requests;
    @Autowired WalletTopUpRequestRepository repository;
    @Autowired WalletAccountRepository wallets;
    @Autowired UserRepository users;
    @Autowired EpointWalletPaymentService epoint;
    TestCsrfToken csrf;
    String token;
    long userId;

    void register() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = csrfResult.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        csrf = new TestCsrfToken(cookie, mapper.readTree(csrfResult.getResponse().getContentAsString()).path("csrfToken").asText());
        String phone = "0" + PHONES.incrementAndGet();
        MvcResult registered = mvc.perform(post("/api/auth/register").cookie(csrf.cookie())
                .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("phone", phone, "firstName", "Payment", "lastName", "Test",
                        "password", "Wallet-safe-2026")))).andExpect(status().isOk()).andReturn();
        token = mapper.readTree(registered.getResponse().getContentAsString()).path("accessToken").asText();
        userId = users.findByNormalizedPhone("+994" + phone.substring(1)).orElseThrow().getId();
    }

    Map<String, Object> callback(WalletTopUpRequestDto request, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_id", request.externalOrderId());
        payload.put("transaction", "tx-" + request.externalOrderId());
        payload.put("status", status);
        payload.put("amount", request.amountAzn().toPlainString());
        return payload;
    }

    void deliver(Map<String, Object> payload) throws Exception {
        String data = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(payload));
        epoint.processCallback(data, EpointSignature.sign(data, PRIVATE_KEY));
    }

    String form(Map<String, Object> payload) throws Exception {
        String data = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(payload));
        return "data=" + URLEncoder.encode(data, StandardCharsets.UTF_8) + "&signature="
                + URLEncoder.encode(EpointSignature.sign(data, PRIVATE_KEY), StandardCharsets.UTF_8);
    }

    void expectCallback(Map<String, Object> payload, int expectedStatus) throws Exception {
        mvc.perform(post("/api/payments/epoint/callback").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(form(payload))).andExpect(status().is(expectedStatus));
    }

    long balance() { return wallets.findByUserId(userId).orElseThrow().getBalance(); }

    void expectLookup(long id, int expectedStatus) throws Exception {
        mvc.perform(get("/api/users/me/wallet/top-up-requests/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().is(expectedStatus));
    }
}
