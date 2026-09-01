package az.turn.api;

import java.time.LocalDateTime;

public record StoredSecureAttachment(
        long id,
        long ownerUserId,
        SecureAttachmentPurpose purpose,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        int widthPixels,
        int heightPixels,
        String sha256,
        LocalDateTime scannedAt
) {
}
