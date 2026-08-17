package az.turn.api;

import java.time.LocalDateTime;

public record AccountDeletionRequestDto(
        long id,
        long userId,
        SupportRequestStatus status,
        String resolutionNote,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
}
