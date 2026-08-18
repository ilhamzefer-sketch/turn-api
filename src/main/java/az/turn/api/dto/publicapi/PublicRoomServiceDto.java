package az.turn.api;

import java.math.BigDecimal;

public record PublicRoomServiceDto(
        long id,
        String name,
        String description,
        BigDecimal price,
        String currency
) {
}
