package az.turn.api;

public record SessionMetadata(
        String userAgent,
        String ipAddress
) {
}
