package az.turn.api;

public record QueueManagerLoginResponse(
        long queueManagerId,
        String username,
        QueueDetailResponse queue,
        String accessToken
) {
}
