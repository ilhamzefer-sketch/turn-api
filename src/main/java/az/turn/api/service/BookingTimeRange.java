package az.turn.api;

import java.time.LocalDateTime;

public record BookingTimeRange(
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime blockingEndAt
) {
}
