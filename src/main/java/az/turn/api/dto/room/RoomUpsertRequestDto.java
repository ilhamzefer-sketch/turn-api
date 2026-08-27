package az.turn.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RoomUpsertRequestDto(
        @NotBlank(message = "Otaq adı mütləqdir.")
        @Size(min = 2, max = 160, message = "Otaq adı 2-160 simvol olmalıdır.")
        String name,
        @Size(max = 80, message = "Otaq nömrəsi və ya kodu maksimum 80 simvol ola bilər.")
        String roomNumberOrCode,
        @Size(max = 2000, message = "Açıqlama maksimum 2000 simvol ola bilər.")
        String description,
        @Size(max = 2000, message = "Qeyd maksimum 2000 simvol ola bilər.")
        String notes,
        @Size(max = 60, message = "Saat qurşağı maksimum 60 simvol ola bilər.")
        String timezone,
        @NotNull(message = "Rezervasiya rejimi mütləqdir.")
        ReservationMode reservationMode,
        @Min(value = 1, message = "Standart müddət ən azı 1 dəqiqə olmalıdır.")
        @Max(value = 1440, message = "Standart müddət maksimum 1440 dəqiqə ola bilər.")
        int defaultSlotDurationMinutes,
        @NotNull(message = "Görünürlük mütləqdir.")
        RoomVisibility visibility,
        @Size(max = 500, message = "Şəxsi ünvan maksimum 500 simvol ola bilər.")
        String personalPublicAddress,
        @DecimalMin(value = "-90", message = "Enlik -90-dan kiçik ola bilməz.")
        @DecimalMax(value = "90", message = "Enlik 90-dan böyük ola bilməz.")
        BigDecimal personalLatitude,
        @DecimalMin(value = "-180", message = "Uzunluq -180-dan kiçik ola bilməz.")
        @DecimalMax(value = "180", message = "Uzunluq 180-dan böyük ola bilməz.")
        BigDecimal personalLongitude
) {
}
