package az.turn.api;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WeeklyAvailabilityRuleRequestDto(
        @NotNull(message = "Həftənin günü mütləqdir.")
        DayOfWeek dayOfWeek,
        @NotNull(message = "Başlanğıc saatı mütləqdir.")
        LocalTime startTime,
        @NotNull(message = "Bitmə saatı mütləqdir.")
        LocalTime endTime,
        @NotNull(message = "İş intervalının aktivlik vəziyyəti mütləqdir.")
        Boolean active
) {
}
