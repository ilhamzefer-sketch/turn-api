package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WeeklyAvailabilityReplaceRequestDto(
        @NotNull(message = "Həftəlik cədvəl siyahısı mütləqdir.")
        @Size(max = 56, message = "Həftəlik cədvəl maksimum 56 interval saxlaya bilər.")
        List<@Valid WeeklyAvailabilityRuleRequestDto> rules
) {
}
