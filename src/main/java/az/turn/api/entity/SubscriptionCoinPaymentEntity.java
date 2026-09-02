package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "subscription_coin_payments")
public class SubscriptionCoinPaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_user_id", nullable = false)
    private UserEntity payerUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_subscription_id", nullable = false)
    private ProviderSubscriptionEntity providerSubscription;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlanEntity subscriptionPlan;
    @Column(nullable = false, unique = true)
    private Long walletTransactionId;
    @Column(nullable = false, length = 80)
    private String idempotencyKey;
    @Column(nullable = false)
    private long amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;
    @Column(nullable = false, unique = true, length = 180)
    private String paymentReference;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private boolean subscriptionStateCaptured;

    @Column(nullable = false)
    private boolean subscriptionExistedBefore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_subscription_plan_id")
    private SubscriptionPlanEntity previousSubscriptionPlan;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SubscriptionStatus previousSubscriptionStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BillingPeriod previousBillingPeriod;

    private Integer previousRoomLimit;
    private Integer previousEmployeeLimit;
    private LocalDateTime previousStartsAt;
    private LocalDateTime previousExpiresAt;
    private LocalDateTime previousGraceEndsAt;
    private LocalDateTime previousUsageGraceEndsAt;
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_admin_id")
    private AdminAccountEntity cancelledByAdmin;

    @Column(length = 1000)
    private String cancellationReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_wallet_transaction_id", unique = true)
    private WalletTransactionEntity refundWalletTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fraud_top_up_request_id")
    private WalletTopUpRequestEntity fraudTopUpRequest;

    public Long getId() { return id; }
    public UserEntity getPayerUser() { return payerUser; }
    public void setPayerUser(UserEntity value) { this.payerUser = value; }
    public ProviderSubscriptionEntity getProviderSubscription() { return providerSubscription; }
    public void setProviderSubscription(ProviderSubscriptionEntity value) { this.providerSubscription = value; }
    public SubscriptionPlanEntity getSubscriptionPlan() { return subscriptionPlan; }
    public void setSubscriptionPlan(SubscriptionPlanEntity value) { this.subscriptionPlan = value; }
    public Long getWalletTransactionId() { return walletTransactionId; }
    public void setWalletTransactionId(Long value) { this.walletTransactionId = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public long getAmount() { return amount; }
    public void setAmount(long value) { this.amount = value; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus value) { this.status = value; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String value) { this.paymentReference = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
    public boolean isSubscriptionStateCaptured() { return subscriptionStateCaptured; }
    public boolean isSubscriptionExistedBefore() { return subscriptionExistedBefore; }
    public SubscriptionPlanEntity getPreviousSubscriptionPlan() { return previousSubscriptionPlan; }
    public SubscriptionStatus getPreviousSubscriptionStatus() { return previousSubscriptionStatus; }
    public BillingPeriod getPreviousBillingPeriod() { return previousBillingPeriod; }
    public Integer getPreviousRoomLimit() { return previousRoomLimit; }
    public Integer getPreviousEmployeeLimit() { return previousEmployeeLimit; }
    public LocalDateTime getPreviousStartsAt() { return previousStartsAt; }
    public LocalDateTime getPreviousExpiresAt() { return previousExpiresAt; }
    public LocalDateTime getPreviousGraceEndsAt() { return previousGraceEndsAt; }
    public LocalDateTime getPreviousUsageGraceEndsAt() { return previousUsageGraceEndsAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public AdminAccountEntity getCancelledByAdmin() { return cancelledByAdmin; }
    public String getCancellationReason() { return cancellationReason; }
    public WalletTransactionEntity getRefundWalletTransaction() { return refundWalletTransaction; }
    public WalletTopUpRequestEntity getFraudTopUpRequest() { return fraudTopUpRequest; }

    public void captureSubscriptionState(ProviderSubscriptionEntity subscription, boolean existedBefore) {
        if (subscriptionStateCaptured) {
            throw new IllegalStateException("Abunəliyin əvvəlki vəziyyəti artıq saxlanılıb.");
        }
        subscriptionStateCaptured = true;
        subscriptionExistedBefore = existedBefore;
        if (!existedBefore) return;
        if (subscription == null || subscription.getPlan() == null || subscription.getStatus() == null
                || subscription.getBillingPeriod() == null) {
            throw new IllegalArgumentException("Abunəliyin əvvəlki vəziyyəti tam deyil.");
        }
        previousSubscriptionPlan = subscription.getPlan();
        previousSubscriptionStatus = subscription.getStatus();
        previousBillingPeriod = subscription.getBillingPeriod();
        previousRoomLimit = subscription.getRoomLimit();
        previousEmployeeLimit = subscription.getEmployeeLimit();
        previousStartsAt = subscription.getStartsAt();
        previousExpiresAt = subscription.getExpiresAt();
        previousGraceEndsAt = subscription.getGraceEndsAt();
        previousUsageGraceEndsAt = subscription.getUsageGraceEndsAt();
    }

    public void cancelForFraud(
            ProviderSubscriptionEntity lockedSubscription,
            AdminAccountEntity admin,
            WalletTopUpRequestEntity topUpRequest,
            WalletTransactionEntity refundTransaction,
            String reason,
            LocalDateTime cancellationTime
    ) {
        requireCancellableState(lockedSubscription, topUpRequest, refundTransaction);
        String normalizedReason = Objects.requireNonNull(reason).trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("Abunəlik ləğvi səbəbi boş ola bilməz.");
        }
        restoreSubscription(lockedSubscription, cancellationTime);
        status = PaymentStatus.CANCELLED;
        cancelledAt = Objects.requireNonNull(cancellationTime);
        cancelledByAdmin = Objects.requireNonNull(admin);
        cancellationReason = normalizedReason;
        refundWalletTransaction = refundTransaction;
        fraudTopUpRequest = topUpRequest;
    }

    private void requireCancellableState(
            ProviderSubscriptionEntity lockedSubscription,
            WalletTopUpRequestEntity topUpRequest,
            WalletTransactionEntity refundTransaction
    ) {
        if (status != PaymentStatus.COMPLETED || !subscriptionStateCaptured) {
            throw new IllegalStateException("Abunəlik ödənişi fırıldaq səbəbilə ləğv edilə bilməz.");
        }
        if (lockedSubscription == null || lockedSubscription.getId() == null
                || !lockedSubscription.getId().equals(providerSubscription.getId())) {
            throw new IllegalArgumentException("Abunəlik ödənişə uyğun deyil.");
        }
        if (topUpRequest == null || topUpRequest.getUser().getId() == null
                || !topUpRequest.getUser().getId().equals(payerUser.getId())) {
            throw new IllegalArgumentException("Balans artırma sorğusu ödəyiciyə uyğun deyil.");
        }
        if (refundTransaction == null || refundTransaction.getType() != WalletTransactionType.REFUND
                || refundTransaction.getAmount() != amount
                || !refundTransaction.getWalletAccount().getUser().getId().equals(payerUser.getId())) {
            throw new IllegalArgumentException("Coin geri qaytarma əməliyyatı ödənişə uyğun deyil.");
        }
    }

    private void restoreSubscription(ProviderSubscriptionEntity subscription, LocalDateTime cancellationTime) {
        if (subscriptionExistedBefore) {
            subscription.setPlan(previousSubscriptionPlan);
            subscription.setStatus(previousSubscriptionStatus);
            subscription.setBillingPeriod(previousBillingPeriod);
            subscription.setRoomLimit(previousRoomLimit);
            subscription.setEmployeeLimit(previousEmployeeLimit);
            subscription.setStartsAt(previousStartsAt);
            subscription.setExpiresAt(previousExpiresAt);
            subscription.setGraceEndsAt(previousGraceEndsAt);
            subscription.setUsageGraceEndsAt(previousUsageGraceEndsAt);
        } else {
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            subscription.setExpiresAt(cancellationTime);
            subscription.setGraceEndsAt(null);
            subscription.setUsageGraceEndsAt(null);
        }
        subscription.setUpdatedAt(cancellationTime);
    }
}
