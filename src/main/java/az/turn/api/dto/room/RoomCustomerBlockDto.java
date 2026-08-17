package az.turn.api;

import java.time.LocalDateTime;

public record RoomCustomerBlockDto(
        long id,
        long roomId,
        long customerUserId,
        String reason,
        boolean active,
        long blockedByUserId,
        LocalDateTime createdAt,
        LocalDateTime revokedAt
) {
}
