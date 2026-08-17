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
@Table(name = "room_assignments")
public class RoomAssignmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoomRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoomAssignmentStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private UserEntity invitedByUser;
    @Column(nullable = false)
    private boolean showPhonePublicly;
    @Column(nullable = false)
    private LocalDateTime invitedAt;
    @Column
    private LocalDateTime respondedAt;
    @Column
    private LocalDateTime revokedAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RoomEntity getRoom() { return room; }
    public void setRoom(RoomEntity room) { this.room = room; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public RoomRole getRole() { return role; }
    public void setRole(RoomRole role) { this.role = role; }
    public RoomAssignmentStatus getStatus() { return status; }
    public void setStatus(RoomAssignmentStatus status) { this.status = status; }
    public UserEntity getInvitedByUser() { return invitedByUser; }
    public void setInvitedByUser(UserEntity value) { this.invitedByUser = value; }
    public boolean isShowPhonePublicly() { return showPhonePublicly; }
    public void setShowPhonePublicly(boolean value) { this.showPhonePublicly = value; }
    public LocalDateTime getInvitedAt() { return invitedAt; }
    public void setInvitedAt(LocalDateTime value) { this.invitedAt = value; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime value) { this.respondedAt = value; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime value) { this.revokedAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (invitedAt == null) invitedAt = now;
        if (createdAt == null) createdAt = now;
    }
}
