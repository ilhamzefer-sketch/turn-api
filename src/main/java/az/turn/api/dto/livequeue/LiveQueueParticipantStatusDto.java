package az.turn.api;

public record LiveQueueParticipantStatusDto(
        String publicReference,
        LiveQueueEntryStatus status,
        long peopleAhead,
        long approximateWaitingMinutes,
        String currentPublicReference,
        boolean acceptingNewEntries
) {
}
