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
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "secure_attachments")
public class SecureAttachmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private UserEntity ownerUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SecureAttachmentPurpose purpose;

    @Column(nullable = false, unique = true, length = 80)
    private String storageKey;

    @Column(nullable = false, length = 180)
    private String originalFilename;

    @Column(nullable = false, length = 30)
    private String mediaType;

    @Column(nullable = false, length = 10)
    private String fileExtension;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int widthPixels;

    @Column(nullable = false)
    private int heightPixels;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecureAttachmentScanStatus scanStatus;

    @Column(nullable = false)
    private LocalDateTime scannedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SecureAttachmentEntity() {
    }

    public SecureAttachmentEntity(
            UserEntity ownerUser,
            SecureAttachmentPurpose purpose,
            String storageKey,
            String originalFilename,
            String mediaType,
            String fileExtension,
            long sizeBytes,
            int widthPixels,
            int heightPixels,
            String sha256,
            LocalDateTime scannedAt
    ) {
        this.ownerUser = Objects.requireNonNull(ownerUser);
        this.purpose = Objects.requireNonNull(purpose);
        this.storageKey = Objects.requireNonNull(storageKey);
        this.originalFilename = Objects.requireNonNull(originalFilename);
        this.mediaType = Objects.requireNonNull(mediaType);
        this.fileExtension = Objects.requireNonNull(fileExtension);
        this.sizeBytes = sizeBytes;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.sha256 = Objects.requireNonNull(sha256);
        scanStatus = SecureAttachmentScanStatus.CLEAN;
        this.scannedAt = Objects.requireNonNull(scannedAt);
        createdAt = scannedAt;
    }

    public Long getId() { return id; }
    public UserEntity getOwnerUser() { return ownerUser; }
    public SecureAttachmentPurpose getPurpose() { return purpose; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMediaType() { return mediaType; }
    public String getFileExtension() { return fileExtension; }
    public long getSizeBytes() { return sizeBytes; }
    public int getWidthPixels() { return widthPixels; }
    public int getHeightPixels() { return heightPixels; }
    public String getSha256() { return sha256; }
    public SecureAttachmentScanStatus getScanStatus() { return scanStatus; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
