package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class WalletIntegrationTests {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletAccountRepository walletAccountRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WalletAccountProvisioningService provisioningService;

    @Autowired
    private WalletTransactionService transactionService;

    @Autowired
    private WalletQueryService queryService;

    @Test
    void appliesCreditAndDebitWithCompleteLedgerBalances() {
        UserEntity user = createUser("+994501290001");
        WalletAccountEntity wallet = provisioningService.provision(user);

        WalletTransactionDto credit = transactionService.apply(user.getId(), adminCredit(100, "admin-credit-1"));
        WalletTransactionDto debit = transactionService.apply(
                user.getId(),
                userDebit(user.getId(), 30, "subscription-1")
        );

        assertThat(credit.balanceBefore()).isZero();
        assertThat(credit.balanceAfter()).isEqualTo(100);
        assertThat(debit.direction()).isEqualTo(WalletTransactionDirection.DEBIT);
        assertThat(debit.balanceBefore()).isEqualTo(100);
        assertThat(debit.balanceAfter()).isEqualTo(70);
        assertThat(walletAccountRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(70);
        assertThat(walletTransactionRepository.countByWalletAccountId(wallet.getId())).isEqualTo(2);
    }

    @Test
    void replaysTheSameReferenceWithoutChangingBalanceTwice() {
        UserEntity user = createUser("+994501290002");
        WalletAccountEntity wallet = provisioningService.provision(user);
        WalletTransactionCommandDto command = adminCredit(50, "admin-credit-replay");

        WalletTransactionDto first = transactionService.apply(user.getId(), command);
        WalletTransactionDto replay = transactionService.apply(user.getId(), command);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(walletAccountRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(50);
        assertThat(walletTransactionRepository.countByWalletAccountId(wallet.getId())).isEqualTo(1);
    }

    @Test
    void rejectsAReferenceReusedForDifferentContent() {
        UserEntity user = createUser("+994501290003");
        provisioningService.provision(user);
        transactionService.apply(user.getId(), adminCredit(50, "admin-credit-conflict"));

        assertThatThrownBy(() -> transactionService.apply(
                user.getId(),
                adminCredit(60, "admin-credit-conflict")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsDebitWhenBalanceIsInsufficient() {
        UserEntity user = createUser("+994501290004");
        WalletAccountEntity wallet = provisioningService.provision(user);

        assertThatThrownBy(() -> transactionService.apply(
                user.getId(),
                userDebit(user.getId(), 1, "subscription-insufficient")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));
        assertThat(walletAccountRepository.findById(wallet.getId()).orElseThrow().getBalance()).isZero();
        assertThat(walletTransactionRepository.countByWalletAccountId(wallet.getId())).isZero();
    }

    @Test
    void requiresAnAdminReferenceAndReasonForAdminCredit() {
        UserEntity user = createUser("+994501290005");
        provisioningService.provision(user);
        WalletTransactionCommandDto missingReason = new WalletTransactionCommandDto(
                WalletTransactionType.ADMIN_CREDIT,
                10,
                WalletActorType.ADMIN,
                null,
                "admin",
                "admin-credit-no-reason",
                null
        );

        assertThatThrownBy(() -> transactionService.apply(user.getId(), missingReason))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void returnsBoundedHistoryInNewestFirstOrder() {
        UserEntity user = createUser("+994501290006");
        provisioningService.provision(user);
        transactionService.apply(user.getId(), adminCredit(20, "history-first"));
        transactionService.apply(user.getId(), adminCredit(30, "history-second"));

        WalletTransactionPageDto firstPage = queryService.transactions(user.getId(), 0, 1);
        WalletTransactionPageDto secondPage = queryService.transactions(user.getId(), 1, 1);

        assertThat(firstPage.items()).singleElement()
                .extracting(WalletTransactionDto::referenceKey)
                .isEqualTo("history-second");
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.items()).singleElement()
                .extracting(WalletTransactionDto::referenceKey)
                .isEqualTo("history-first");
    }

    private UserEntity createUser(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Wallet");
        user.setLastName("Test");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private WalletTransactionCommandDto adminCredit(long amount, String reference) {
        return new WalletTransactionCommandDto(
                WalletTransactionType.ADMIN_CREDIT,
                amount,
                WalletActorType.ADMIN,
                null,
                "bootstrap-admin",
                reference,
                "Manual test credit"
        );
    }

    private WalletTransactionCommandDto userDebit(long userId, long amount, String reference) {
        return new WalletTransactionCommandDto(
                WalletTransactionType.SUBSCRIPTION_PAYMENT,
                amount,
                WalletActorType.USER,
                userId,
                null,
                reference,
                "Subscription test"
        );
    }
}
