package az.turn.api;

import java.time.LocalDateTime;

public record QrCredentialDto(
        long id,
        long roomId,
        QrCredentialType type,
        boolean active,
        String token,
        LocalDateTime createdAt,
        LocalDateTime revokedAt
) {
}
