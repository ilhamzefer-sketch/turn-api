package az.turn.api;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BookingRescheduleRequestDto(
        @NotNull(message = "Yeni rezervasiya saatı mütləqdir.")
        LocalDateTime startAt
) {
}
