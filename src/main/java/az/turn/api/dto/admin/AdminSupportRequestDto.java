package az.turn.api;

import java.time.LocalDateTime;

public record AdminSupportRequestDto(
        long id,
        long userId,
        String firstName,
        String lastName,
        String phone,
        UserSupportRequestType requestType,
        String message,
        SupportRequestStatus status,
        Long attachmentId,
        String attachmentMediaType,
        Long attachmentSizeBytes,
        String attachmentFilename,
        String adminResponse,
        String reviewedByAdmin,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime reviewedAt
) {
}
