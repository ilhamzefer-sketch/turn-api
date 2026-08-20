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
@Table(name = "qr_credentials")
public class QrCredentialEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(unique = true, length = 128)
    private String publicToken;
    @Column(unique = true, length = 64)
    private String legacyTokenHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QrCredentialType type;
    @Column(nullable = false)
    private boolean active;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserEntity createdByUser;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column
    private LocalDateTime revokedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RoomEntity getRoom() { return room; }
    public void setRoom(RoomEntity value) { this.room = value; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String value) { this.tokenHash = value; }
    public String getPublicToken() { return publicToken; }
    public void setPublicToken(String value) { this.publicToken = value; }
    public String getLegacyTokenHash() { return legacyTokenHash; }
    public void setLegacyTokenHash(String value) { this.legacyTokenHash = value; }
    public QrCredentialType getType() { return type; }
    public void setType(QrCredentialType value) { this.type = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
    public UserEntity getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(UserEntity value) { this.createdByUser = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime value) { this.revokedAt = value; }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
