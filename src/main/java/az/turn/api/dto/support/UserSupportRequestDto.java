package az.turn.api;

import java.time.LocalDateTime;

public record UserSupportRequestDto(
        long id,
        UserSupportRequestType requestType,
        String message,
        SupportRequestStatus status,
        boolean hasAttachment,
        String attachmentMediaType,
        Long attachmentSizeBytes,
        String adminResponse,
        String reviewedByAdmin,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime reviewedAt
) {
}
