package az.turn.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityExceptionUpsertRequestDto(
        @NotNull(message = "Xüsusi tarix mütləqdir.")
        LocalDate date,
        @NotNull(message = "Xüsusi tarix növü mütləqdir.")
        AvailabilityExceptionType type,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 500, message = "Səbəb maksimum 500 simvol ola bilər.")
        String reason
) {
}
