package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "room_customer_blocks")
public class RoomCustomerBlockEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_user_id", nullable = false)
    private UserEntity customerUser;
    @Column(nullable = false, length = 1000)
    private String reason;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_by_user_id", nullable = false)
    private UserEntity blockedByUser;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column
    private LocalDateTime revokedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_user_id")
    private UserEntity revokedByUser;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public RoomEntity getRoom() { return room; }
    public void setRoom(RoomEntity value) { this.room = value; }
    public UserEntity getCustomerUser() { return customerUser; }
    public void setCustomerUser(UserEntity value) { this.customerUser = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public UserEntity getBlockedByUser() { return blockedByUser; }
    public void setBlockedByUser(UserEntity value) { this.blockedByUser = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime value) { this.revokedAt = value; }
    public UserEntity getRevokedByUser() { return revokedByUser; }
    public void setRevokedByUser(UserEntity value) { this.revokedByUser = value; }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        active = true;
    }
}
