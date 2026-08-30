package az.turn.api;

import java.time.LocalDateTime;

public record AdminAccountDto(
        long id,
        String username,
        String displayName,
        boolean active,
        String createdByUsername,
        LocalDateTime createdAt
) {
}
