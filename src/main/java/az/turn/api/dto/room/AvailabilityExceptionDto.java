package az.turn.api;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityExceptionDto(
        long id,
        long roomId,
        LocalDate date,
        AvailabilityExceptionType type,
        LocalTime startTime,
        LocalTime endTime,
        String reason
) {
}
