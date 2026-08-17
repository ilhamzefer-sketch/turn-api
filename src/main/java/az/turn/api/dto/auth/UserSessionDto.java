package az.turn.api;

import java.time.LocalDateTime;

public record UserSessionDto(
        long id,
        boolean current,
        String userAgent,
        String ipAddress,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt
) {
}
