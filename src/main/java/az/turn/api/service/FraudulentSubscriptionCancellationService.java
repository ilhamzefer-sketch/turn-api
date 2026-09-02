package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FraudulentSubscriptionCancellationService {
    private final SubscriptionCoinPaymentRepository paymentRepository;
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final WalletTransactionService walletTransactionService;
    private final WalletTransactionRepository walletTransactionRepository;

    public FraudulentSubscriptionCancellationService(
            SubscriptionCoinPaymentRepository paymentRepository,
            ProviderSubscriptionRepository subscriptionRepository,
            WalletTransactionService walletTransactionService,
            WalletTransactionRepository walletTransactionRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.walletTransactionService = walletTransactionService;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    public List<Long> recoverSpentCoins(
            WalletTopUpRequestEntity request,
            WalletAccountEntity wallet,
            AdminAccountEntity admin,
            String adminUsername,
            String reason,
            LocalDateTime now
    ) {
        List<Long> cancelledPaymentIds = new ArrayList<>();
        if (wallet.getBalance() >= request.getCoinAmount()) {
            return cancelledPaymentIds;
        }
        List<SubscriptionCoinPaymentEntity> payments = paymentRepository.findForFraudRecovery(
                request.getUser().getId(),
                PaymentStatus.COMPLETED,
                request.getWalletTransaction().getCreatedAt()
        );
        for (SubscriptionCoinPaymentEntity payment : payments) {
            if (wallet.getBalance() >= request.getCoinAmount()) {
                break;
            }
            cancelPayment(payment, request, admin, adminUsername, reason, now);
            cancelledPaymentIds.add(payment.getId());
        }
        if (wallet.getBalance() < request.getCoinAmount()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Coin-ləri geri çəkmək üçün balans və ləğv edilə bilən abunəlik ödənişləri kifayət etmir."
            );
        }
        return cancelledPaymentIds;
    }

    private void cancelPayment(
            SubscriptionCoinPaymentEntity payment,
            WalletTopUpRequestEntity request,
            AdminAccountEntity admin,
            String adminUsername,
            String reason,
            LocalDateTime now
    ) {
        if (!payment.isSubscriptionStateCaptured()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Abunəliyin əvvəlki vəziyyəti saxlanılmadığı üçün avtomatik ləğv mümkün deyil."
            );
        }
        ProviderSubscriptionEntity subscription = subscriptionRepository
                .findByIdForUpdate(payment.getProviderSubscription().getId())
                .orElseThrow(() -> new IllegalStateException("Abunəlik tapılmadı."));
        WalletTransactionDto refund = walletTransactionService.apply(
                request.getUser().getId(),
                new WalletTransactionCommandDto(
                        WalletTransactionType.REFUND,
                        payment.getAmount(),
                        WalletActorType.ADMIN,
                        null,
                        adminUsername,
                        "top-up-fraud-refund:" + request.getId() + ":payment:" + payment.getId(),
                        "Fırıldaq balans artırmasına bağlı abunəlik ləğv edildi."
                )
        );
        WalletTransactionEntity refundTransaction = walletTransactionRepository.findById(refund.id())
                .orElseThrow(() -> new IllegalStateException("Coin geri qaytarma əməliyyatı saxlanılmadı."));
        payment.cancelForFraud(subscription, admin, request, refundTransaction, reason, now);
        subscriptionRepository.save(subscription);
        paymentRepository.save(payment);
    }
}
