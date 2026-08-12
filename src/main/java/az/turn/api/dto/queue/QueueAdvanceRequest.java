package az.turn.api;

public record QueueAdvanceRequest(
        Long registrationId,
        Long queueManagerId
) {
}
