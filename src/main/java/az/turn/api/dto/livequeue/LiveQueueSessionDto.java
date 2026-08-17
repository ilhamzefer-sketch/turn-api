package az.turn.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record LiveQueueSessionDto(
        long id,
        long roomId,
        String roomName,
        LocalDate serviceDate,
        LiveQueueSessionStatus status,
        LiveQueueAcceptanceOverride acceptanceOverride,
        boolean acceptingNewEntries,
        LocalDateTime nextOpeningAt,
        LocalDateTime nextResetAt,
        String currentPublicReference,
        long waitingCount,
        long skippedCount,
        long activeCount,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        List<LiveQueueEntryDto> entries
) {
}
