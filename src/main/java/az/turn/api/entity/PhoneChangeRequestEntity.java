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
@Table(name = "phone_change_requests")
public class PhoneChangeRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(nullable = false, length = 13)
    private String currentNormalizedPhone;
    @Column(nullable = false, length = 13)
    private String requestedNormalizedPhone;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportRequestStatus status;
    @Column(length = 120)
    private String reviewedByAdmin;
    @Column(length = 2000)
    private String resolutionNote;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column
    private LocalDateTime resolvedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity value) { this.user = value; }
    public String getCurrentNormalizedPhone() { return currentNormalizedPhone; }
    public void setCurrentNormalizedPhone(String value) { this.currentNormalizedPhone = value; }
    public String getRequestedNormalizedPhone() { return requestedNormalizedPhone; }
    public void setRequestedNormalizedPhone(String value) { this.requestedNormalizedPhone = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public SupportRequestStatus getStatus() { return status; }
    public void setStatus(SupportRequestStatus value) { this.status = value; }
    public String getReviewedByAdmin() { return reviewedByAdmin; }
    public void setReviewedByAdmin(String value) { this.reviewedByAdmin = value; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String value) { this.resolutionNote = value; }
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
