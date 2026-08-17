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
@Table(name = "business_memberships")
public class BusinessMembershipEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private BusinessEntity business;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessMembershipStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private UserEntity invitedByUser;
    @Column(length = 80)
    private String invitedFirstName;
    @Column(length = 80)
    private String invitedLastName;
    @Column(nullable = false)
    private boolean createdPendingUser;
    @Column(nullable = false)
    private LocalDateTime invitedAt;
    @Column
    private LocalDateTime acceptedAt;
    @Column
    private LocalDateTime rejectedAt;
    @Column
    private LocalDateTime removedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BusinessEntity getBusiness() { return business; }
    public void setBusiness(BusinessEntity business) { this.business = business; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public BusinessRole getRole() { return role; }
    public void setRole(BusinessRole role) { this.role = role; }
    public BusinessMembershipStatus getStatus() { return status; }
    public void setStatus(BusinessMembershipStatus status) { this.status = status; }
    public UserEntity getInvitedByUser() { return invitedByUser; }
    public void setInvitedByUser(UserEntity value) { this.invitedByUser = value; }
    public String getInvitedFirstName() { return invitedFirstName; }
    public void setInvitedFirstName(String value) { this.invitedFirstName = value; }
    public String getInvitedLastName() { return invitedLastName; }
    public void setInvitedLastName(String value) { this.invitedLastName = value; }
    public boolean isCreatedPendingUser() { return createdPendingUser; }
    public void setCreatedPendingUser(boolean value) { this.createdPendingUser = value; }
    public LocalDateTime getInvitedAt() { return invitedAt; }
    public void setInvitedAt(LocalDateTime value) { this.invitedAt = value; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime value) { this.acceptedAt = value; }
    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime value) { this.rejectedAt = value; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime value) { this.removedAt = value; }

    @PrePersist
    public void prePersist() {
        if (invitedAt == null) invitedAt = LocalDateTime.now();
    }
}
