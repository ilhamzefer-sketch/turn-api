package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "wallet_top_up_packages")
public class WalletTopUpPackageEntity {
    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, unique = true)
    private int amountAzn;

    @Column(nullable = false, unique = true)
    private long coinAmount;

    @Column(nullable = false, unique = true, length = 500)
    private String paymentUrl;

    @Column(nullable = false, unique = true)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected WalletTopUpPackageEntity() {
    }

    public WalletTopUpPackageEntity(
            String code,
            int amountAzn,
            long coinAmount,
            String paymentUrl,
            int displayOrder,
            boolean active,
            LocalDateTime createdAt
    ) {
        this.code = code;
        this.amountAzn = amountAzn;
        this.coinAmount = coinAmount;
        this.paymentUrl = paymentUrl;
        this.displayOrder = displayOrder;
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getCode() {
        return code;
    }

    public int getAmountAzn() {
        return amountAzn;
    }

    public long getCoinAmount() {
        return coinAmount;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
