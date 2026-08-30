package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 60)
    private String code;
    @Column(nullable = false, length = 120)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingPeriod billingPeriod;
    @Column(nullable = false)
    private long amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ProviderScopeType scopeType;
    @Column
    private Long coinPrice;
    @Column(nullable = false)
    private int roomLimit;
    @Column(nullable = false)
    private int employeeLimit;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public BillingPeriod getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(BillingPeriod value) { this.billingPeriod = value; }
    public long getAmount() { return amount; }
    public void setAmount(long value) { this.amount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public ProviderScopeType getScopeType() { return scopeType; }
    public void setScopeType(ProviderScopeType value) { this.scopeType = value; }
    public Long getCoinPrice() { return coinPrice; }
    public void setCoinPrice(Long value) { this.coinPrice = value; }
    public int getRoomLimit() { return roomLimit; }
    public void setRoomLimit(int value) { this.roomLimit = value; }
    public int getEmployeeLimit() { return employeeLimit; }
    public void setEmployeeLimit(int value) { this.employeeLimit = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
