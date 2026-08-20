package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token_history")
public class RefreshTokenHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refresh_token_id", nullable = false)
    private Long refreshTokenId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 500)
    private String tokenHash;

    @Column(name = "rotated_at", nullable = false)
    private LocalDateTime rotatedAt;

    public Long getId() {
        return id;
    }

    public Long getRefreshTokenId() {
        return refreshTokenId;
    }

    public void setRefreshTokenId(Long refreshTokenId) {
        this.refreshTokenId = refreshTokenId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getRotatedAt() {
        return rotatedAt;
    }

    @PrePersist
    public void prePersist() {
        if (rotatedAt == null) {
            rotatedAt = LocalDateTime.now();
        }
    }
}
