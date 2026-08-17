package az.turn.api;

import java.time.LocalDateTime;

public record LiveQueueEntryDto(
        long id,
        String publicReference,
        long queuePosition,
        LiveQueueEntryStatus status,
        LiveQueueEntrySource source,
        String displayName,
        String phone,
        Long linkedUserId,
        String internalNote,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime removedAt
) {
}
