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
@Table(name = "account_deletion_requests")
public class AccountDeletionRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportRequestStatus status;
    @Column(nullable = false)
    private LocalDateTime requestedAt;
    @Column(length = 120)
    private String processedByAdmin;
    @Column
    private LocalDateTime processedAt;
    @Column(length = 2000)
    private String resolutionNote;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity value) { this.user = value; }
    public SupportRequestStatus getStatus() { return status; }
    public void setStatus(SupportRequestStatus value) { this.status = value; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime value) { this.requestedAt = value; }
    public String getProcessedByAdmin() { return processedByAdmin; }
    public void setProcessedByAdmin(String value) { this.processedByAdmin = value; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime value) { this.processedAt = value; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String value) { this.resolutionNote = value; }

    @PrePersist
    public void prePersist() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (status == null) status = SupportRequestStatus.OPEN;
    }
}
