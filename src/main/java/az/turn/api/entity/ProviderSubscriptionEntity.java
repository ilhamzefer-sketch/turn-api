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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "provider_subscriptions")
public class ProviderSubscriptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProviderScopeType scopeType;
    @Column(nullable = false)
    private Long scopeId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlanEntity plan;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingPeriod billingPeriod;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status;
    @Column(nullable = false)
    private int roomLimit;
    @Column(nullable = false)
    private int employeeLimit;
    @Column
    private LocalDateTime startsAt;
    @Column
    private LocalDateTime expiresAt;
    @Column
    private LocalDateTime graceEndsAt;
    @Column
    private LocalDateTime usageGraceEndsAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public ProviderScopeType getScopeType() { return scopeType; }
    public void setScopeType(ProviderScopeType value) { this.scopeType = value; }
    public Long getScopeId() { return scopeId; }
    public void setScopeId(Long value) { this.scopeId = value; }
    public SubscriptionPlanEntity getPlan() { return plan; }
    public void setPlan(SubscriptionPlanEntity value) { this.plan = value; }
    public BillingPeriod getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(BillingPeriod value) { this.billingPeriod = value; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus value) { this.status = value; }
    public int getRoomLimit() { return roomLimit; }
    public void setRoomLimit(int value) { this.roomLimit = value; }
    public int getEmployeeLimit() { return employeeLimit; }
    public void setEmployeeLimit(int value) { this.employeeLimit = value; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime value) { this.startsAt = value; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { this.expiresAt = value; }
    public LocalDateTime getGraceEndsAt() { return graceEndsAt; }
    public void setGraceEndsAt(LocalDateTime value) { this.graceEndsAt = value; }
    public LocalDateTime getUsageGraceEndsAt() { return usageGraceEndsAt; }
    public void setUsageGraceEndsAt(LocalDateTime value) { this.usageGraceEndsAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}
