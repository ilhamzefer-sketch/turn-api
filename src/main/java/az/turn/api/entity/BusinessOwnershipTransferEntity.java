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
@Table(name = "business_ownership_transfers")
public class BusinessOwnershipTransferEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private BusinessEntity business;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_owner_user_id", nullable = false)
    private UserEntity fromOwnerUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_admin_user_id", nullable = false)
    private UserEntity toAdminUser;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OwnershipTransferStatus status;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column
    private LocalDateTime respondedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public BusinessEntity getBusiness() { return business; }
    public void setBusiness(BusinessEntity value) { this.business = value; }
    public UserEntity getFromOwnerUser() { return fromOwnerUser; }
    public void setFromOwnerUser(UserEntity value) { this.fromOwnerUser = value; }
    public UserEntity getToAdminUser() { return toAdminUser; }
    public void setToAdminUser(UserEntity value) { this.toAdminUser = value; }
    public OwnershipTransferStatus getStatus() { return status; }
    public void setStatus(OwnershipTransferStatus value) { this.status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime value) { this.respondedAt = value; }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = OwnershipTransferStatus.PENDING_ACCEPTANCE;
    }
}
