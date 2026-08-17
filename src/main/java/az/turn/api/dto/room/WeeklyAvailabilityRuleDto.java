package az.turn.api;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WeeklyAvailabilityRuleDto(
        long id,
        long roomId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        boolean active
) {
}
