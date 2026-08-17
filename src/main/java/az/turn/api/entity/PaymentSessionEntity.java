package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_sessions")
public class PaymentSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionToken;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column
    private RegistrationType registrationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentPurpose paymentPurpose;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_subscription_id")
    private ProviderSubscriptionEntity providerSubscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id")
    private SubscriptionPlanEntity subscriptionPlan;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Column
    private String firstName;

    @Column
    private String lastName;

    @Column
    private String email;

    @Column
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id")
    private RegistrationEntity registration;

    @Column(nullable = false)
    private String cardHolder;

    @Column(nullable = false)
    private String cardLast4;

    @Column(nullable = false)
    private String sandboxOutcome;

    @Column
    private String paymentReference;

    @Column
    private String externalOrderId;

    @Column
    private String externalOrderPassword;

    @Column
    private String externalHppUrl;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime authenticationIssuedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public RegistrationType getRegistrationType() { return registrationType; }
    public void setRegistrationType(RegistrationType registrationType) { this.registrationType = registrationType; }
    public PaymentPurpose getPaymentPurpose() { return paymentPurpose; }
    public void setPaymentPurpose(PaymentPurpose value) { this.paymentPurpose = value; }
    public ProviderSubscriptionEntity getProviderSubscription() { return providerSubscription; }
    public void setProviderSubscription(ProviderSubscriptionEntity value) { this.providerSubscription = value; }
    public SubscriptionPlanEntity getSubscriptionPlan() { return subscriptionPlan; }
    public void setSubscriptionPlan(SubscriptionPlanEntity value) { this.subscriptionPlan = value; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public RegistrationEntity getRegistration() { return registration; }
    public void setRegistration(RegistrationEntity registration) { this.registration = registration; }
    public String getCardHolder() { return cardHolder; }
    public void setCardHolder(String cardHolder) { this.cardHolder = cardHolder; }
    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }
    public String getSandboxOutcome() { return sandboxOutcome; }
    public void setSandboxOutcome(String sandboxOutcome) { this.sandboxOutcome = sandboxOutcome; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getExternalOrderId() { return externalOrderId; }
    public void setExternalOrderId(String externalOrderId) { this.externalOrderId = externalOrderId; }
    public String getExternalOrderPassword() { return externalOrderPassword; }
    public void setExternalOrderPassword(String externalOrderPassword) { this.externalOrderPassword = externalOrderPassword; }
    public String getExternalHppUrl() { return externalHppUrl; }
    public void setExternalHppUrl(String externalHppUrl) { this.externalHppUrl = externalHppUrl; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getAuthenticationIssuedAt() { return authenticationIssuedAt; }
    public void setAuthenticationIssuedAt(LocalDateTime authenticationIssuedAt) { this.authenticationIssuedAt = authenticationIssuedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @PrePersist
    public void prePersist() {
        if (paymentPurpose == null) paymentPurpose = PaymentPurpose.LEGACY_REGISTRATION;
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
