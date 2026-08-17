package az.turn.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record BookingCreateRequestDto(
        @NotNull(message = "Otaq mütləqdir.")
        @Positive(message = "Otaq identifikatoru müsbət olmalıdır.")
        Long roomId,
        @NotNull(message = "Rezervasiya saatı mütləqdir.")
        LocalDateTime startAt,
        @Positive(message = "Xidmət identifikatoru müsbət olmalıdır.")
        Long serviceId,
        @Size(max = 1000, message = "Müştəri qeydi maksimum 1000 simvol ola bilər.")
        String customerNote
) {
}
