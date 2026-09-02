package az.turn.api;

import java.time.LocalDateTime;

public record QrCredentialDto(
        long id,
        long roomId,
        QrCredentialType type,
        boolean active,
        String token,
        String posterTitle,
        LocalDateTime createdAt,
        LocalDateTime revokedAt
) {
}
