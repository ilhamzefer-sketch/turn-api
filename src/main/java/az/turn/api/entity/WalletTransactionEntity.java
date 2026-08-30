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
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "wallet_transactions")
public class WalletTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_account_id", nullable = false)
    private WalletAccountEntity walletAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private WalletTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WalletTransactionDirection direction;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long balanceBefore;

    @Column(nullable = false)
    private long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletActorType actorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserEntity actorUser;

    @Column(length = 160)
    private String actorReference;

    @Column(nullable = false, length = 180)
    private String referenceKey;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected WalletTransactionEntity() {
    }

    public WalletTransactionEntity(
            WalletAccountEntity walletAccount,
            WalletTransactionType type,
            long amount,
            long balanceBefore,
            long balanceAfter,
            WalletActorType actorType,
            UserEntity actorUser,
            String actorReference,
            String referenceKey,
            String description,
            LocalDateTime createdAt
    ) {
        this.walletAccount = walletAccount;
        this.type = type;
        this.direction = type.direction();
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.actorType = actorType;
        this.actorUser = actorUser;
        this.actorReference = actorReference;
        this.referenceKey = referenceKey;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public WalletAccountEntity getWalletAccount() {
        return walletAccount;
    }

    public WalletTransactionType getType() {
        return type;
    }

    public WalletTransactionDirection getDirection() {
        return direction;
    }

    public long getAmount() {
        return amount;
    }

    public long getBalanceBefore() {
        return balanceBefore;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public WalletActorType getActorType() {
        return actorType;
    }

    public UserEntity getActorUser() {
        return actorUser;
    }

    public String getActorReference() {
        return actorReference;
    }

    public String getReferenceKey() {
        return referenceKey;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
