package az.turn.api;

public record LiveQueuePublicEntryDto(
        String publicReference,
        long queuePosition,
        LiveQueueEntryStatus status
) {
}
