package az.turn.api;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class WalletPaymentLifecyclePostgresTests extends WalletPaymentTestSupport {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static EpointTestServer provider;
    @Autowired JdbcTemplate jdbc;

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
    void concurrentDuplicatesCreditExactlyOnceAndDoNotDemotePaidState() throws Exception {
        WalletTopUpRequestDto request = requests.create(userId, "AZN_10");
        Map<String, Object> success = callback(request, "success");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                results.add(pool.submit(() -> { deliver(success); return null; }));
            }
            for (Future<?> result : results) result.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        deliver(callback(request, "failed"));
        assertThat(balance()).isEqualTo(100);
        assertThat(requests.get(userId, request.id()).status()).isEqualTo(WalletTopUpRequestStatus.PAID);
        assertThat(jdbc.queryForObject("select count(*) from wallet_transactions where reference_key = ?", Long.class,
                "top-up-request:" + request.id())).isEqualTo(1);
    }

    @Test
    void signedSuccessWinsAfterFailureExpiryAndReplacement() throws Exception {
        WalletTopUpRequestDto failed = requests.create(userId, "AZN_3");
        deliver(callback(failed, "failed"));
        assertThat(requests.get(userId, failed.id()).status()).isEqualTo(WalletTopUpRequestStatus.PAYMENT_FAILED);
        WalletTopUpRequestDto expired = requests.create(userId, "AZN_5");
        jdbc.update("update wallet_top_up_requests set clicked_at = clicked_at - interval '31 minutes', "
                + "receipt_deadline_at = receipt_deadline_at - interval '31 minutes' where id = ?", expired.id());
        WalletTopUpRequestDto replaced = requests.create(userId, "AZN_10");
        WalletTopUpRequestDto active = requests.create(userId, "AZN_15");
        assertThat(requests.get(userId, expired.id()).status()).isEqualTo(WalletTopUpRequestStatus.EXPIRED);
        assertThat(requests.get(userId, replaced.id()).status()).isEqualTo(WalletTopUpRequestStatus.SUPERSEDED);
        for (WalletTopUpRequestDto request : List.of(failed, expired, replaced)) {
            deliver(callback(request, "success"));
            deliver(callback(request, "success"));
            assertThat(requests.get(userId, request.id()).status()).isEqualTo(WalletTopUpRequestStatus.PAID);
        }
        assertThat(balance()).isEqualTo(180);
        assertThat(requests.active(userId).id()).isEqualTo(active.id());
    }

    @Test
    void retriesReuseCheckoutAndReturnUrlsIdentifyTheRequest() {
        WalletTopUpRequestDto first = requests.create(userId, "AZN_10");
        WalletTopUpRequestDto retry = requests.create(userId, " azn_10 ");
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(provider.calls).hasValue(1);
        assertThat(retry.checkoutState()).isEqualTo(WalletCheckoutState.READY);
        assertThat(retry.paymentProvider()).isEqualTo("epoint");
        assertThat(retry.receiptUploadOpen()).isFalse();
        assertThat(requests.active(userId).id()).isEqualTo(first.id());
        assertThat(provider.lastPayload.path("currency").asText()).isEqualTo("AZN");
        assertThat(provider.lastPayload.path("success_redirect_url").asText()).contains("requestId=" + first.id());
        assertThat(provider.lastPayload.path("error_redirect_url").asText()).contains("requestId=" + first.id());
    }

    @Test
    void callbackBeforeCheckoutResponseSeesCommittedAttemptAndIsNotOverwritten() throws Exception {
        provider.beforeResponse = payload -> {
            try {
                deliver(Map.of("order_id", payload.path("order_id").asText(), "status", "success",
                        "amount", payload.path("amount").asText(), "transaction", "tx-" + payload.path("order_id").asText()));
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        };
        WalletTopUpRequestDto request = requests.create(userId, "AZN_10");
        assertThat(request.status()).isEqualTo(WalletTopUpRequestStatus.PAID);
        assertThat(request.checkoutState()).isEqualTo(WalletCheckoutState.READY);
        assertThat(request.paymentUrl()).isNull();
        assertThat(balance()).isEqualTo(100);
    }

    @Test
    void timeoutRemainsRecoverableAndRetryDoesNotSendAnotherCharge() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        provider.beforeResponse = payload -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        };
        CompletableFuture<WalletTopUpRequestDto> creating = CompletableFuture.supplyAsync(() -> requests.create(userId, "AZN_10"));
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            WalletTopUpRequestDto pending = requests.active(userId);
            assertThat(pending.checkoutState()).isEqualTo(WalletCheckoutState.PREPARING);
            assertThat(pending.paymentUrl()).isNull();
            assertThat(requests.create(userId, "AZN_10").id()).isEqualTo(pending.id());
            WalletTopUpRequestDto timedOut = creating.get(3, TimeUnit.SECONDS);
            assertThat(timedOut.checkoutState()).isEqualTo(WalletCheckoutState.UNKNOWN);
            assertThat(requests.create(userId, "AZN_10").id()).isEqualTo(pending.id());
            assertThat(provider.calls).hasValue(1);
            deliver(callback(timedOut, "success"));
            assertThat(balance()).isEqualTo(100);
            assertThat(requests.get(userId, pending.id()).status()).isEqualTo(WalletTopUpRequestStatus.PAID);
        } finally { release.countDown(); }
    }

    @Test
    void providerErrorsPreserveTheOrderForALaterCallback() throws Exception {
        provider.responseCode = 503;
        WalletTopUpRequestDto request = requests.create(userId, "AZN_5");
        assertThat(request.checkoutState()).isEqualTo(WalletCheckoutState.UNKNOWN);
        assertThat(request.paymentUrl()).isNull();
        assertThat(requests.create(userId, "AZN_5").id()).isEqualTo(request.id());
        assertThat(provider.calls).hasValue(1);
        deliver(callback(request, "success"));
        assertThat(balance()).isEqualTo(50);
    }

    @Test
    void concurrentCreationLeavesOneResumableAttempt() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try { requests.create(userId, "AZN_10"); }
                    catch (WalletTopUpException exception) {
                        assertThat(exception.getMessage()).contains("Əvvəlki");
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) result.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        WalletTopUpRequestDto active = requests.active(userId);
        assertThat(active.checkoutState()).isEqualTo(WalletCheckoutState.READY);
        assertThat(provider.calls).hasValue(1);
        assertThat(requests.create(userId, "AZN_10").id()).isEqualTo(active.id());
    }

    @Test
    void cardRequestsCannotEnterReceiptCreditFlow() {
        WalletTopUpRequestDto request = requests.create(userId, "AZN_10");
        assertThatThrownBy(() -> requests.uploadReceipt(userId, request.id(), null))
                .isInstanceOf(WalletTopUpException.class).hasMessageContaining("Kartla");
        assertThat(balance()).isZero();
        assertThat(requests.active(userId).id()).isEqualTo(request.id());
    }

    @Test
    void rejectsUntrustedOrMismatchedPaymentEvidence() throws Exception {
        WalletTopUpRequestDto request = requests.create(userId, "AZN_10");
        mvc.perform(post("/api/payments/epoint/callback").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("data=e30%3D&signature=invalid")).andExpect(status().isBadRequest());
        Map<String, Object> missingAmount = callback(request, "success");
        missingAmount.remove("amount");
        expectCallback(missingAmount, 400);
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of(
                "amount", "9.99", "currency", "USD", "transaction", "another-transaction", "operation_code", "001").entrySet()) {
            Map<String, Object> payload = callback(request, "success");
            payload.put(invalid.getKey(), invalid.getValue());
            expectCallback(payload, 409);
        }
        Map<String, Object> malformed = callback(request, "success");
        malformed.put("amount", "not-an-amount");
        expectCallback(malformed, 400);
        malformed.put("order_id", "9999999999999999999999999999999");
        expectCallback(malformed, 400);
        malformed.put("order_id", request.externalOrderId() + "0");
        expectCallback(malformed, 409);
        assertThat(balance()).isZero();
        assertThat(requests.get(userId, request.id()).status()).isEqualTo(WalletTopUpRequestStatus.AWAITING_RECEIPT);
    }

    @Test
    void intermediateStatusDoesNotBecomeFailureAndLookupIsOwnerScoped() throws Exception {
        WalletTopUpRequestDto request = requests.create(userId, "AZN_3");
        deliver(callback(request, "new"));
        deliver(callback(request, "server_error"));
        assertThat(requests.active(userId).id()).isEqualTo(request.id());
        assertThat(balance()).isZero();
        expectLookup(request.id(), 200);
        register();
        expectLookup(request.id(), 404);
        expectLookup(Long.MAX_VALUE, 404);
    }
}
