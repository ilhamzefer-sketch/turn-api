package az.turn.api;

import java.time.LocalDateTime;
import java.util.List;

public record LiveQueuePublicDto(
        long roomId,
        String roomName,
        Long sessionId,
        LiveQueueSessionStatus status,
        boolean acceptingNewEntries,
        LocalDateTime nextOpeningAt,
        LocalDateTime nextResetAt,
        String currentPublicReference,
        long waitingCount,
        long approximateWaitingMinutes,
        List<LiveQueuePublicEntryDto> entries
) {
}
