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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_ownership_disputes")
public class AccountOwnershipDisputeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disputed_user_id")
    private UserEntity disputedUser;
    @Column(nullable = false, length = 13)
    private String disputedPhone;
    @Column(nullable = false, length = 160)
    private String claimantName;
    @Column(nullable = false, length = 13)
    private String claimantContactPhone;
    @Column(nullable = false, length = 2000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportRequestStatus status;
    @Column(length = 2000)
    private String resolutionNote;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DisputeResolutionAction resolutionAction;
    @Column(length = 120)
    private String reviewedByAdmin;
    @Column
    private LocalDateTime passwordResetRequiredAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column
    private LocalDateTime resolvedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public UserEntity getDisputedUser() { return disputedUser; }
    public void setDisputedUser(UserEntity value) { this.disputedUser = value; }
    public String getDisputedPhone() { return disputedPhone; }
    public void setDisputedPhone(String value) { this.disputedPhone = value; }
    public String getClaimantName() { return claimantName; }
    public void setClaimantName(String value) { this.claimantName = value; }
    public String getClaimantContactPhone() { return claimantContactPhone; }
    public void setClaimantContactPhone(String value) { this.claimantContactPhone = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public SupportRequestStatus getStatus() { return status; }
    public void setStatus(SupportRequestStatus value) { this.status = value; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String value) { this.resolutionNote = value; }
    public DisputeResolutionAction getResolutionAction() { return resolutionAction; }
    public void setResolutionAction(DisputeResolutionAction value) { this.resolutionAction = value; }
    public String getReviewedByAdmin() { return reviewedByAdmin; }
    public void setReviewedByAdmin(String value) { this.reviewedByAdmin = value; }
    public LocalDateTime getPasswordResetRequiredAt() { return passwordResetRequiredAt; }
    public void setPasswordResetRequiredAt(LocalDateTime value) { this.passwordResetRequiredAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime value) { this.resolvedAt = value; }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = SupportRequestStatus.OPEN;
    }
}
