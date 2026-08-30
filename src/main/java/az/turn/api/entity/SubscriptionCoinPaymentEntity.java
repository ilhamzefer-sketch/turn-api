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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

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
}
