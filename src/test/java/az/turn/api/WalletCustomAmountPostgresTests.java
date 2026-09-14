package az.turn.api;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.payment.epoint.public-key=test-merchant",
        "app.payment.epoint.private-key=local-test-private-key",
        "app.payment.epoint.connect-timeout=200ms",
        "app.payment.epoint.request-timeout=1s",
        "app.wallet.manual-top-up-enabled=false",
        "app.security.rate-limit.auth-per-minute=1000"
})
@AutoConfigureMockMvc
@Testcontainers
class WalletCustomAmountPostgresTests extends WalletPaymentTestSupport {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static EpointTestServer provider;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminTopUpRequestMapper adminMapper;
    @Autowired TransactionTemplate transactions;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        provider = new EpointTestServer();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("app.payment.epoint.api-base-url", provider::url);
    }

    @AfterAll
    static void stopProvider() { if (provider != null) provider.close(); }

    @BeforeEach
    void setup() throws Exception {
        provider.reset();
        register();
    }

    @Test
    void minimumAmountCreatesOneCoinAndIgnoresClientCoinClaims() throws Exception {
        WalletTopUpRequestDto request = create("{\"amountAzn\":\"0.10\",\"coinAmount\":99999}");
        assertThat(request.amountAzn()).isEqualByComparingTo("0.10");
        assertThat(request.coinAmount()).isEqualTo(1);
        assertThat(request.packageCode()).isNull();
        AdminTopUpRequestDto adminRequest = transactions.execute(ignored ->
                adminMapper.toDto(repository.findById(request.id()).orElseThrow()));
        assertThat(adminRequest.packageCode()).isNull();
        assertThat(adminRequest.coinAmount()).isEqualTo(1);
        assertThat(new BigDecimal(provider.lastPayload.path("amount").asText())).isEqualByComparingTo("0.10");
        assertThat(balance()).isZero();
        Map<String, Object> wrongAmount = callback(request, "success");
        wrongAmount.put("amount", "0.09");
        expectCallback(wrongAmount, 409);
        deliver(callback(request, "success"));
        deliver(callback(request, "success"));
        assertThat(balance()).isEqualTo(1);
        assertThat(requests.get(userId, request.id()).status()).isEqualTo(WalletTopUpRequestStatus.PAID);
        mvc.perform(get("/api/users/me/wallet/top-up-requests/" + request.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coinAmount").value(1));
        register();
        expectLookup(request.id(), 404);
    }

    @Test
    void exposesExactCustomAmountLimitsAlongsideLegacyPackages() throws Exception {
        mvc.perform(get("/api/users/me/wallet/top-up-options").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customAmountEnabled").value(true))
                .andExpect(jsonPath("$.minimumAmountAzn").value(0.10))
                .andExpect(jsonPath("$.maximumAmountAzn").value(50))
                .andExpect(jsonPath("$.amountStepAzn").value(0.10))
                .andExpect(jsonPath("$.coinsPerAzn").value(10))
                .andExpect(jsonPath("$.packages.length()").value(5));
        WalletTopUpRequestDto maximum = create("{\"amountAzn\":50}");
        assertThat(maximum.coinAmount()).isEqualTo(500);
    }

    @Test
    void rejectsInvalidOrAmbiguousAmountsWithoutCreatingCheckout() throws Exception {
        long before = repository.count();
        for (String body : List.of("{}", "{\"amountAzn\":null}", "{\"packageCode\":\"AZN_3\",\"amountAzn\":3}",
                "{\"packageCode\":\"\"}", "{\"amountAzn\":\"bad\"}", "{\"amountAzn\":0}", "{\"amountAzn\":-1}",
                "{\"amountAzn\":0.09}", "{\"amountAzn\":0.11}", "{\"amountAzn\":0.101}",
                "{\"amountAzn\":50.10}", "{\"amountAzn\":999999999999999999999999}")) {
            mvc.perform(post("/api/users/me/wallet/top-up-requests").cookie(csrf.cookie())
                    .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/users/me/wallet/top-up-requests").cookie(csrf.cookie())
                .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amountAzn\":0.10}"))
                .andExpect(status().isUnauthorized());
        assertThat(repository.count()).isEqualTo(before);
        assertThat(provider.calls).hasValue(0);
    }

    @Test
    void amountRetriesReuseAttemptsAndLateSuccessCreditsSavedAmount() throws Exception {
        WalletTopUpRequestDto first = create("{\"amountAzn\":7.30}");
        assertThat(create("{\"amountAzn\":\"7.3\"}").id()).isEqualTo(first.id());
        assertThat(provider.calls).hasValue(1);
        WalletTopUpRequestDto replacement = create("{\"amountAzn\":8.40}");
        assertThat(requests.get(userId, first.id()).status()).isEqualTo(WalletTopUpRequestStatus.SUPERSEDED);
        deliver(callback(first, "success"));
        deliver(callback(first, "success"));
        assertThat(balance()).isEqualTo(73);
        assertThat(requests.active(userId).id()).isEqualTo(replacement.id());
        deliver(callback(replacement, "success"));
        assertThat(balance()).isEqualTo(157);
    }

    @Test
    void unknownCustomCheckoutCannotBeResentOrChangedUntilResolved() throws Exception {
        provider.responseCode = 503;
        WalletTopUpRequestDto first = create("{\"amountAzn\":0.10}");
        assertThat(first.checkoutState()).isEqualTo(WalletCheckoutState.UNKNOWN);
        assertThat(create("{\"amountAzn\":0.10}").id()).isEqualTo(first.id());
        assertThatThrownBy(() -> requests.create(userId, new WalletTopUpCreateRequestDto(null, new BigDecimal("0.20"))))
                .isInstanceOf(WalletTopUpException.class);
        assertThat(provider.calls).hasValue(1);
        deliver(callback(first, "success"));
        assertThat(balance()).isEqualTo(1);
    }

    @Test
    void equivalentLegacyAndCustomAmountsShareOneCheckout() throws Exception {
        WalletTopUpRequestDto legacy = requests.create(userId, "AZN_3");
        assertThat(create("{\"amountAzn\":3.00}").id()).isEqualTo(legacy.id());
        assertThat(provider.calls).hasValue(1);
        deliver(callback(legacy, "failed"));
        WalletTopUpRequestDto custom = create("{\"amountAzn\":5.00}");
        assertThat(requests.create(userId, "AZN_5").id()).isEqualTo(custom.id());
        assertThat(provider.calls).hasValue(2);
    }

    @Test
    void databaseRejectsIncorrectCoinsAndManualCustomPayments() throws Exception {
        WalletTopUpRequestDto request = create("{\"amountAzn\":7.30}");
        for (String assignment : List.of("coin_amount=74", "amount_azn=0.09, coin_amount=1",
                "amount_azn=50.10, coin_amount=501",
                "payment_provider='manual', checkout_state='NOT_REQUIRED'", "package_code='AZN_3'")) {
            assertThatThrownBy(() -> jdbc.update("update wallet_top_up_requests set " + assignment + " where id=?", request.id()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(requests.get(userId, request.id()).coinAmount()).isEqualTo(73);
    }

    private WalletTopUpRequestDto create(String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/users/me/wallet/top-up-requests").cookie(csrf.cookie())
                .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), WalletTopUpRequestDto.class);
    }
}
