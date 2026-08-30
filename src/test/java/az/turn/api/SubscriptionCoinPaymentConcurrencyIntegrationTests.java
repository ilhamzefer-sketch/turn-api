package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.subscription.enforcement-enabled=true")
class SubscriptionCoinPaymentConcurrencyIntegrationTests {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletAccountProvisioningService provisioningService;
    @Autowired
    private WalletTransactionService walletTransactionService;
    @Autowired
    private WalletAccountRepository walletAccountRepository;
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;
    @Autowired
    private SubscriptionCoinPaymentService coinPaymentService;
    @Autowired
    private SubscriptionCoinPaymentRepository coinPaymentRepository;
    @Autowired
    private IndividualWorkspaceService workspaceService;

    @Test
    void concurrentReplayChargesAndExtendsOnlyOnce() throws Exception {
        UserEntity owner = user("+994501490001");
        credit(owner, 30, "concurrent-replay-credit");
        IndividualWorkspaceResponseDto workspace = workspace(owner, "Concurrent replay workspace");
        SubscriptionCoinPurchaseRequestDto request = request(workspace.id(), "concurrent-replay");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Long> first = executor.submit(() -> purchaseIdAfterSignal(owner, request, ready, start));
            Future<Long> second = executor.submit(() -> purchaseIdAfterSignal(owner, request, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
            assertSingleDebit(owner, "subscription:" + owner.getId() + ":concurrent-replay");
            assertThat(coinPaymentRepository.findByPayerUserIdAndIdempotencyKey(
                    owner.getId(), "concurrent-replay"
            )).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDistinctPurchasesCannotOverdrawWallet() throws Exception {
        UserEntity owner = user("+994501490002");
        credit(owner, 30, "concurrent-balance-credit");
        IndividualWorkspaceResponseDto workspace = workspace(owner, "Concurrent balance workspace");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpStatus> first = executor.submit(() -> purchaseStatusAfterSignal(
                    owner, request(workspace.id(), "concurrent-balance-one"), ready, start
            ));
            Future<HttpStatus> second = executor.submit(() -> purchaseStatusAfterSignal(
                    owner, request(workspace.id(), "concurrent-balance-two"), ready, start
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(first.get()).isIn(HttpStatus.OK, HttpStatus.PAYMENT_REQUIRED);
            assertThat(second.get()).isIn(HttpStatus.OK, HttpStatus.PAYMENT_REQUIRED);
            assertThat(walletAccountRepository.findByUserId(owner.getId()).orElseThrow().getBalance()).isZero();
            assertThat(walletTransactionRepository.countByWalletAccountId(wallet(owner).getId())).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private Long purchaseIdAfterSignal(
            UserEntity owner,
            SubscriptionCoinPurchaseRequestDto request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return coinPaymentService.purchase(owner.getId(), request).paymentId();
    }

    private HttpStatus purchaseStatusAfterSignal(
            UserEntity owner,
            SubscriptionCoinPurchaseRequestDto request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            coinPaymentService.purchase(owner.getId(), request);
            return HttpStatus.OK;
        } catch (ResponseStatusException exception) {
            return HttpStatus.valueOf(exception.getStatusCode().value());
        }
    }

    private UserEntity user(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Concurrent");
        user.setLastName("Owner");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        UserEntity saved = userRepository.saveAndFlush(user);
        provisioningService.provision(saved);
        return saved;
    }

    private IndividualWorkspaceResponseDto workspace(UserEntity owner, String name) {
        return workspaceService.create(
                owner.getId(),
                new IndividualWorkspaceCreateRequestDto(name, "Asia/Baku")
        );
    }

    private void credit(UserEntity owner, long amount, String reference) {
        walletTransactionService.apply(owner.getId(), new WalletTransactionCommandDto(
                WalletTransactionType.ADMIN_CREDIT,
                amount,
                WalletActorType.ADMIN,
                null,
                "concurrency-test-admin",
                reference,
                "Concurrency test credit"
        ));
    }

    private SubscriptionCoinPurchaseRequestDto request(long workspaceId, String idempotencyKey) {
        return new SubscriptionCoinPurchaseRequestDto(
                ProviderScopeType.INDIVIDUAL_WORKSPACE,
                workspaceId,
                "INDIVIDUAL_MONTHLY",
                idempotencyKey
        );
    }

    private void assertSingleDebit(UserEntity owner, String reference) {
        WalletAccountEntity wallet = wallet(owner);
        assertThat(wallet.getBalance()).isZero();
        assertThat(walletTransactionRepository.countByWalletAccountId(wallet.getId())).isEqualTo(2);
        assertThat(walletTransactionRepository.findByWalletAccountIdAndReferenceKey(wallet.getId(), reference))
                .isPresent();
    }

    private WalletAccountEntity wallet(UserEntity owner) {
        return walletAccountRepository.findByUserId(owner.getId()).orElseThrow();
    }
}
