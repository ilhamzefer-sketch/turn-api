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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(unique = true, length = 13)
    private String normalizedPhone;

    @Column
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserStatus status;

    @Column(length = 80)
    private String invitedFirstName;

    @Column(length = 80)
    private String invitedLastName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private UserEntity createdByUser;

    @Column(nullable = false)
    private int failedLoginAttempts;

    @Column(nullable = false)
    private int confirmedWalletFraudCount;

    @Column
    private LocalDateTime lockedUntil;

    @Column
    private LocalDateTime activatedAt;

    @Column
    private LocalDateTime lastLoginAt;

    @Column
    private LocalDateTime passwordChangedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getNormalizedPhone() {
        return normalizedPhone;
    }

    public void setNormalizedPhone(String normalizedPhone) {
        this.normalizedPhone = normalizedPhone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getInvitedFirstName() {
        return invitedFirstName;
    }

    public void setInvitedFirstName(String invitedFirstName) {
        this.invitedFirstName = invitedFirstName;
    }

    public String getInvitedLastName() {
        return invitedLastName;
    }

    public void setInvitedLastName(String invitedLastName) {
        this.invitedLastName = invitedLastName;
    }

    public UserEntity getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(UserEntity createdByUser) {
        this.createdByUser = createdByUser;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public int getConfirmedWalletFraudCount() {
        return confirmedWalletFraudCount;
    }

    public void setConfirmedWalletFraudCount(int confirmedWalletFraudCount) {
        if (confirmedWalletFraudCount < 0) {
            throw new IllegalArgumentException("Təsdiqlənmiş fırıldaq sayı mənfi ola bilməz.");
        }
        this.confirmedWalletFraudCount = confirmedWalletFraudCount;
    }

    public int registerConfirmedWalletFraud() {
        confirmedWalletFraudCount = Math.incrementExact(confirmedWalletFraudCount);
        return confirmedWalletFraudCount;
    }

    public boolean requiresManualWalletTopUpReview() {
        return confirmedWalletFraudCount >= 3;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
