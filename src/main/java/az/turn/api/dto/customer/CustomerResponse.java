package az.turn.api;

import java.time.LocalDateTime;

public record CustomerResponse(
        long id,
        String firstName,
        String lastName,
        String email,
        LocalDateTime createdAt,
        String accessToken
) {
}
