package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_accounts")
public class AdminAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    @Column(nullable = false, length = 120)
    private String displayName;
    @Column(nullable = false, length = 255)
    private String passwordHash;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private boolean mustChangeCredentials;
    @Column
    private LocalDateTime credentialsChangedAt;
    @Column(length = 50)
    private String createdByUsername;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getUsername() { return username; }
    public void setUsername(String value) { this.username = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { this.displayName = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { this.passwordHash = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
    public boolean isMustChangeCredentials() { return mustChangeCredentials; }
    public void setMustChangeCredentials(boolean value) { this.mustChangeCredentials = value; }
    public LocalDateTime getCredentialsChangedAt() { return credentialsChangedAt; }
    public void setCredentialsChangedAt(LocalDateTime value) { this.credentialsChangedAt = value; }
    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String value) { this.createdByUsername = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}
