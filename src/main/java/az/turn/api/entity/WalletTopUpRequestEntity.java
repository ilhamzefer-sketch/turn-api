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
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "wallet_top_up_requests")
public class WalletTopUpRequestEntity {
    private static final long RECEIPT_WINDOW_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(unique = true)
    private Long activeUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_code", nullable = false)
    private WalletTopUpPackageEntity topUpPackage;

    @Column(nullable = false)
    private int amountAzn;

    @Column(nullable = false)
    private long coinAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 500)
    private String paymentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WalletTopUpRequestStatus status;

    @Column(nullable = false)
    private LocalDateTime clickedAt;

    @Column(nullable = false)
    private LocalDateTime receiptDeadlineAt;

    private LocalDateTime receiptUploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private AdminAccountEntity reviewedByAdmin;

    private LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String resolutionNote;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_attachment_id", unique = true)
    private SecureAttachmentEntity receiptAttachment;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_transaction_id", unique = true)
    private WalletTransactionEntity walletTransaction;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected WalletTopUpRequestEntity() {
    }

    public WalletTopUpRequestEntity(
            UserEntity user,
            WalletTopUpPackageEntity topUpPackage,
            LocalDateTime clickedAt
    ) {
        this.user = Objects.requireNonNull(user);
        if (user.getId() == null) {
            throw new IllegalArgumentException("İstifadəçi saxlanılmış olmalıdır.");
        }
        this.topUpPackage = Objects.requireNonNull(topUpPackage);
        if (!topUpPackage.isActive()) {
            throw new IllegalArgumentException("Balans artırma paketi aktiv deyil.");
        }
        this.clickedAt = Objects.requireNonNull(clickedAt);
        activeUserId = user.getId();
        amountAzn = topUpPackage.getAmountAzn();
        coinAmount = topUpPackage.getCoinAmount();
        currency = "AZN";
        paymentUrl = topUpPackage.getPaymentUrl();
        status = WalletTopUpRequestStatus.AWAITING_RECEIPT;
        receiptDeadlineAt = clickedAt.plusMinutes(RECEIPT_WINDOW_MINUTES);
        createdAt = clickedAt;
        updatedAt = clickedAt;
    }

    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public Long getActiveUserId() {
        return activeUserId;
    }

    public WalletTopUpPackageEntity getTopUpPackage() {
        return topUpPackage;
    }

    public int getAmountAzn() {
        return amountAzn;
    }

    public long getCoinAmount() {
        return coinAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public WalletTopUpRequestStatus getStatus() {
        return status;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public LocalDateTime getReceiptDeadlineAt() {
        return receiptDeadlineAt;
    }

    public LocalDateTime getReceiptUploadedAt() {
        return receiptUploadedAt;
    }

    public AdminAccountEntity getReviewedByAdmin() {
        return reviewedByAdmin;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public WalletTransactionEntity getWalletTransaction() {
        return walletTransaction;
    }

    public SecureAttachmentEntity getReceiptAttachment() {
        return receiptAttachment;
    }

    public long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isReceiptWindowOpen(LocalDateTime now) {
        return status == WalletTopUpRequestStatus.AWAITING_RECEIPT
                && Objects.requireNonNull(now).isBefore(receiptDeadlineAt);
    }

    public void submitReceipt(SecureAttachmentEntity attachment, LocalDateTime submittedAt) {
        requireStatus(WalletTopUpRequestStatus.AWAITING_RECEIPT);
        if (attachment == null || attachment.getPurpose() != SecureAttachmentPurpose.PAYMENT_RECEIPT
                || attachment.getOwnerUser().getId() == null
                || !attachment.getOwnerUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Çek əlavəsi bu istifadəçiyə aid deyil.");
        }
        submitReceipt(submittedAt);
        receiptAttachment = attachment;
    }

    public void submitReceipt(LocalDateTime submittedAt) {
        requireStatus(WalletTopUpRequestStatus.AWAITING_RECEIPT);
        LocalDateTime uploadTime = Objects.requireNonNull(submittedAt);
        if (!uploadTime.isBefore(receiptDeadlineAt)) {
            throw new IllegalStateException("Çek yükləmə müddəti bitib.");
        }
        status = WalletTopUpRequestStatus.PENDING_REVIEW;
        receiptUploadedAt = uploadTime;
        updatedAt = uploadTime;
    }

    public boolean expire(LocalDateTime now) {
        LocalDateTime expiryTime = Objects.requireNonNull(now);
        if (status != WalletTopUpRequestStatus.AWAITING_RECEIPT
                || expiryTime.isBefore(receiptDeadlineAt)) {
            return false;
        }
        status = WalletTopUpRequestStatus.EXPIRED;
        activeUserId = null;
        updatedAt = expiryTime;
        return true;
    }

    public void approve(
            AdminAccountEntity admin,
            WalletTransactionEntity transaction,
            LocalDateTime approvedAt
    ) {
        requireStatus(WalletTopUpRequestStatus.PENDING_REVIEW);
        reviewedByAdmin = Objects.requireNonNull(admin);
        walletTransaction = Objects.requireNonNull(transaction);
        reviewedAt = Objects.requireNonNull(approvedAt);
        status = WalletTopUpRequestStatus.APPROVED;
        activeUserId = null;
        updatedAt = approvedAt;
    }

    public void reject(AdminAccountEntity admin, String reason, LocalDateTime rejectedAt) {
        requireStatus(WalletTopUpRequestStatus.PENDING_REVIEW);
        String normalizedReason = Objects.requireNonNull(reason).trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("Rədd səbəbi boş ola bilməz.");
        }
        reviewedByAdmin = Objects.requireNonNull(admin);
        reviewedAt = Objects.requireNonNull(rejectedAt);
        resolutionNote = normalizedReason;
        status = WalletTopUpRequestStatus.REJECTED;
        activeUserId = null;
        updatedAt = rejectedAt;
    }

    private void requireStatus(WalletTopUpRequestStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException("Balans artırma sorğusunun statusu uyğun deyil.");
        }
    }
}
