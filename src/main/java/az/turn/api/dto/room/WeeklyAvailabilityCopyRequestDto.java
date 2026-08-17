package az.turn.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.util.Set;

public record WeeklyAvailabilityCopyRequestDto(
        @NotNull(message = "Mənbə gün mütləqdir.")
        DayOfWeek sourceDay,
        @NotEmpty(message = "Ən azı bir hədəf gün seçilməlidir.")
        @Size(max = 6, message = "Maksimum 6 hədəf gün seçilə bilər.")
        Set<DayOfWeek> targetDays
) {
}
