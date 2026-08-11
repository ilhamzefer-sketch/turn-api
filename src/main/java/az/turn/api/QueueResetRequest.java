package az.turn.api;

public record QueueResetRequest(
        Long registrationId,
        Long queueManagerId
) {
}
