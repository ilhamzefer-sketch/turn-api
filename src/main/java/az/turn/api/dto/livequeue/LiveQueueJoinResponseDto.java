package az.turn.api;

public record LiveQueueJoinResponseDto(
        long sessionId,
        String publicReference,
        long queuePosition,
        LiveQueueEntryStatus status,
        long peopleAhead,
        long approximateWaitingMinutes,
        String currentPublicReference,
        boolean acceptingNewEntries
) {
}
