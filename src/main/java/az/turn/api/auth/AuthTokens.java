package az.turn.api;

import java.time.LocalDateTime;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        LocalDateTime refreshExpiresAt
) {
}
