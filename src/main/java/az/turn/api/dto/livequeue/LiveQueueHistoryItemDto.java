package az.turn.api;

import java.time.LocalDateTime;

public record LiveQueueHistoryItemDto(
        long entryId,
        long roomId,
        String roomName,
        String publicReference,
        long queuePosition,
        LiveQueueEntryStatus status,
        LiveQueueEntrySource source,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
