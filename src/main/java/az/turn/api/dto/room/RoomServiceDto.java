package az.turn.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RoomServiceDto(
        long id,
        long roomId,
        String name,
        String description,
        BigDecimal price,
        String currency,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
