package az.turn.api;

import java.time.OffsetDateTime;

public record SessionInfoDto(
        long id,
        OffsetDateTime serverTime,
        OffsetDateTime lastActivityAt,
        OffsetDateTime idleExpiresAt,
        OffsetDateTime absoluteExpiresAt
) {
}
