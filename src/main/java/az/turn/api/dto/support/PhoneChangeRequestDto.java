package az.turn.api;

import java.time.LocalDateTime;

public record PhoneChangeRequestDto(
        long id,
        long userId,
        String currentPhone,
        String requestedPhone,
        String reason,
        SupportRequestStatus status,
        String resolutionNote,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
}
