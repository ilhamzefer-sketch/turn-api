package az.turn.api;

import java.time.LocalDateTime;

public record CustomerQueueEntryResponse(
        long entryId,
        String displayName,
        Integer rating,
        String ratingNote,
        LocalDateTime joinedAt
) {
}
