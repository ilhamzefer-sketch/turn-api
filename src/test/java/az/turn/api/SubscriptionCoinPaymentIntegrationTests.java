package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.subscription.enforcement-enabled=true")
@Transactional
class SubscriptionCoinPaymentIntegrationTests {
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
    private ProviderSubscriptionRepository subscriptionRepository;
    @Autowired
    private IndividualWorkspaceService workspaceService;
    @Autowired
    private BusinessService businessService;
    @Autowired
    private BranchService branchService;
    @Autowired
    private RoomService roomService;

    @Test
    void chargesThirtyCoinsAndReplaysWithoutASecondDebitOrExtension() {
        UserEntity owner = user("+994501390001");
        credit(owner, 80, "individual-credit");
        IndividualWorkspaceResponseDto workspace = workspaceService.create(
                owner.getId(),
                new IndividualWorkspaceCreateRequestDto("Fərdi iş sahəsi", "Asia/Baku")
        );
        SubscriptionCoinPurchaseRequestDto request = purchaseRequest(
                ProviderScopeType.INDIVIDUAL_WORKSPACE,
                workspace.id(),
                "INDIVIDUAL_MONTHLY",
                "individual-purchase"
        );

        SubscriptionCoinPurchaseDto first = coinPaymentService.purchase(owner.getId(), request);
        SubscriptionCoinPurchaseDto replay = coinPaymentService.purchase(owner.getId(), request);

        assertThat(first.coinsSpent()).isEqualTo(30);
        assertThat(first.balanceAfter()).isEqualTo(50);
        assertThat(replay.paymentId()).isEqualTo(first.paymentId());
        assertThat(replay.subscription().expiresAt()).isEqualTo(first.subscription().expiresAt());
        assertThat(walletAccountRepository.findByUserId(owner.getId()).orElseThrow().getBalance()).isEqualTo(50);
        assertThat(walletTransactionRepository.countByWalletAccountId(
                walletAccountRepository.findByUserId(owner.getId()).orElseThrow().getId()
        )).isEqualTo(2);
        assertThat(coinPaymentRepository.count()).isPositive();
        SubscriptionCoinPaymentEntity payment = coinPaymentRepository.findById(first.paymentId()).orElseThrow();
        assertThat(payment.isSubscriptionStateCaptured()).isTrue();
        assertThat(payment.isSubscriptionExistedBefore()).isFalse();
    }

    @Test
    void capturesTheActiveSubscriptionBeforeARenewalExtendsIt() {
        UserEntity owner = user("+994501390006");
        credit(owner, 60, "renewal-credit");
        IndividualWorkspaceResponseDto workspace = workspaceService.create(
                owner.getId(),
                new IndividualWorkspaceCreateRequestDto("Yenilənən sahə", "Asia/Baku")
        );
        SubscriptionCoinPurchaseDto first = coinPaymentService.purchase(
                owner.getId(),
                purchaseRequest(
                        ProviderScopeType.INDIVIDUAL_WORKSPACE,
                        workspace.id(),
                        "INDIVIDUAL_MONTHLY",
                        "renewal-one"
                )
        );

        SubscriptionCoinPurchaseDto second = coinPaymentService.purchase(
                owner.getId(),
                purchaseRequest(
                        ProviderScopeType.INDIVIDUAL_WORKSPACE,
                        workspace.id(),
                        "INDIVIDUAL_MONTHLY",
                        "renewal-two"
                )
        );

        SubscriptionCoinPaymentEntity payment = coinPaymentRepository.findById(second.paymentId()).orElseThrow();
        assertThat(payment.isSubscriptionStateCaptured()).isTrue();
        assertThat(payment.isSubscriptionExistedBefore()).isTrue();
        assertThat(payment.getPreviousSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(payment.getPreviousExpiresAt()).isEqualTo(first.subscription().expiresAt());
        assertThat(second.subscription().expiresAt()).isAfter(first.subscription().expiresAt());
    }

    @Test
    void rejectsInsufficientBalanceWithoutCreatingSubscriptionOrDebit() {
        UserEntity owner = user("+994501390002");
        IndividualWorkspaceResponseDto workspace = workspaceService.create(
                owner.getId(),
                new IndividualWorkspaceCreateRequestDto("Boş balans", "Asia/Baku")
        );

        assertThatThrownBy(() -> coinPaymentService.purchase(
                owner.getId(),
                purchaseRequest(
                        ProviderScopeType.INDIVIDUAL_WORKSPACE,
                        workspace.id(),
                        "INDIVIDUAL_MONTHLY",
                        "insufficient-purchase"
                )
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));

        assertThat(walletAccountRepository.findByUserId(owner.getId()).orElseThrow().getBalance()).isZero();
        assertThat(subscriptionRepository.findByScopeTypeAndScopeId(
                ProviderScopeType.INDIVIDUAL_WORKSPACE,
                workspace.id()
        )).isEmpty();
    }

    @Test
    void businessPlanCostsOneHundredCoinsAndBlocksTheSixthRoom() {
        UserEntity owner = user("+994501390003");
        credit(owner, 100, "business-credit");
        BusinessResponseDto business = businessService.create(
                owner.getId(),
                new BusinessUpsertRequestDto(
                        "Beş otaqlı biznes", null, null, null, null, "0501390003", "Asia/Baku", null, null
                )
        );
        BranchResponseDto branch = branchService.create(
                business.id(),
                owner.getId(),
                new BranchUpsertRequestDto(
                        "Əsas filial", "Nizami 1", "Bakı", "Nəsimi", null, null, null, null, "Asia/Baku"
                )
        );

        SubscriptionCoinPurchaseDto purchase = coinPaymentService.purchase(
                owner.getId(),
                purchaseRequest(ProviderScopeType.BUSINESS, business.id(), "BUSINESS_MONTHLY", "business-purchase")
        );
        for (int index = 1; index <= 5; index++) {
            roomService.createBusinessRoom(branch.id(), owner.getId(), roomRequest("Otaq " + index));
        }

        assertThat(purchase.coinsSpent()).isEqualTo(100);
        assertThat(purchase.subscription().roomLimit()).isEqualTo(5);
        assertThatThrownBy(() -> roomService.createBusinessRoom(
                branch.id(),
                owner.getId(),
                roomRequest("Altıncı otaq")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getReason()).contains("5 otaq limitinə");
        });
    }

    @Test
    void rejectsAPlanForTheWrongScopeAndAnotherUsersWorkspace() {
        UserEntity owner = user("+994501390004");
        UserEntity stranger = user("+994501390005");
        credit(owner, 100, "scope-owner-credit");
        credit(stranger, 100, "scope-stranger-credit");
        IndividualWorkspaceResponseDto workspace = workspaceService.create(
                owner.getId(),
                new IndividualWorkspaceCreateRequestDto("Qorunan sahə", "Asia/Baku")
        );

        assertThatThrownBy(() -> coinPaymentService.purchase(
                owner.getId(),
                purchaseRequest(ProviderScopeType.INDIVIDUAL_WORKSPACE, workspace.id(), "BUSINESS_MONTHLY", "wrong-plan")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> coinPaymentService.purchase(
                stranger.getId(),
                purchaseRequest(ProviderScopeType.INDIVIDUAL_WORKSPACE, workspace.id(), "INDIVIDUAL_MONTHLY", "wrong-owner")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private UserEntity user(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Coin");
        user.setLastName("Owner");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        UserEntity saved = userRepository.saveAndFlush(user);
        provisioningService.provision(saved);
        return saved;
    }

    private void credit(UserEntity user, long amount, String reference) {
        walletTransactionService.apply(user.getId(), new WalletTransactionCommandDto(
                WalletTransactionType.ADMIN_CREDIT,
                amount,
                WalletActorType.ADMIN,
                null,
                "subscription-test-admin",
                reference,
                "Subscription test credit"
        ));
    }

    private SubscriptionCoinPurchaseRequestDto purchaseRequest(
            ProviderScopeType scopeType,
            long scopeId,
            String planCode,
            String idempotencyKey
    ) {
        return new SubscriptionCoinPurchaseRequestDto(scopeType, scopeId, planCode, idempotencyKey);
    }

    private RoomUpsertRequestDto roomRequest(String name) {
        return new RoomUpsertRequestDto(
                name, null, null, null, "Asia/Baku", ReservationMode.LIVE_QUEUE, 30,
                RoomVisibility.UNLISTED, null, null, null
        );
    }
}
