package az.turn.api;

import java.math.BigDecimal;

public record PublicRoomLocationDto(
        String address,
        String city,
        String district,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
