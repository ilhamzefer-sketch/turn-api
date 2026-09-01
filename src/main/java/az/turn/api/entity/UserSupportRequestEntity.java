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
@Table(name = "user_support_requests")
public class UserSupportRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private UserSupportRequestType requestType;

    @Column(nullable = false, length = 4000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportRequestStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "support_attachment_id", unique = true)
    private SecureAttachmentEntity attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private AdminAccountEntity reviewedByAdmin;

    @Column(name = "admin_response", length = 4000)
    private String adminResponse;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime reviewedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserSupportRequestEntity() {
    }

    public UserSupportRequestEntity(UserEntity user, UserSupportRequestType requestType, String message, LocalDateTime createdAt) {
        this.user = Objects.requireNonNull(user);
        if (user.getId() == null) {
            throw new IllegalArgumentException("İstifadəçi saxlanılmış olmalıdır.");
        }
        this.requestType = Objects.requireNonNull(requestType);
        this.message = requireText(message, "Müraciət mətni boş ola bilməz.");
        this.status = SupportRequestStatus.OPEN;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public UserEntity getUser() { return user; }
    public UserSupportRequestType getRequestType() { return requestType; }
    public String getMessage() { return message; }
    public SupportRequestStatus getStatus() { return status; }
    public SecureAttachmentEntity getAttachment() { return attachment; }
    public AdminAccountEntity getReviewedByAdmin() { return reviewedByAdmin; }
    public String getAdminResponse() { return adminResponse; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public long getVersion() { return version; }

    public void attach(SecureAttachmentEntity value, LocalDateTime attachedAt) {
        requireOpen();
        if (attachment != null) {
            throw new IllegalStateException("Müraciətə artıq şəkil əlavə edilib.");
        }
        if (value == null || value.getPurpose() != SecureAttachmentPurpose.SUPPORT_REQUEST
                || value.getOwnerUser().getId() == null || !value.getOwnerUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Şəkil əlavəsi bu istifadəçiyə aid deyil.");
        }
        attachment = value;
        updatedAt = Objects.requireNonNull(attachedAt);
    }

    public void review(AdminAccountEntity admin, SupportRequestStatus nextStatus, String response, LocalDateTime reviewedAt) {
        requireOpen();
        if (nextStatus != SupportRequestStatus.IN_REVIEW
                && nextStatus != SupportRequestStatus.RESOLVED
                && nextStatus != SupportRequestStatus.REJECTED) {
            throw new IllegalArgumentException("Müraciət statusu uyğun deyil.");
        }
        String normalizedResponse = response == null ? null : response.trim();
        if ((nextStatus == SupportRequestStatus.RESOLVED || nextStatus == SupportRequestStatus.REJECTED)
                && (normalizedResponse == null || normalizedResponse.isEmpty())) {
            throw new IllegalArgumentException("Yekun status üçün cavab mətni tələb olunur.");
        }
        this.reviewedByAdmin = Objects.requireNonNull(admin);
        this.adminResponse = normalizedResponse;
        this.status = nextStatus;
        this.reviewedAt = Objects.requireNonNull(reviewedAt);
        this.updatedAt = reviewedAt;
    }

    private void requireOpen() {
        if (status != SupportRequestStatus.OPEN && status != SupportRequestStatus.IN_REVIEW) {
            throw new IllegalStateException("Müraciət artıq yekunlaşdırılıb.");
        }
    }

    private String requireText(String value, String message) {
        String normalized = Objects.requireNonNull(value).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
