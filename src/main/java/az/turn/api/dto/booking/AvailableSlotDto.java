package az.turn.api;

import java.time.LocalDateTime;

public record AvailableSlotDto(
        LocalDateTime startAt,
        LocalDateTime endAt,
        String timezone
) {
}
