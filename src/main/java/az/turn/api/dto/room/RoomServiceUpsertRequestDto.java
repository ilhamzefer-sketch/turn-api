package az.turn.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RoomServiceUpsertRequestDto(
        @NotBlank(message = "Xidmət adı mütləqdir.")
        @Size(max = 160, message = "Xidmət adı maksimum 160 simvol ola bilər.")
        String name,
        @Size(max = 1000, message = "Xidmət açıqlaması maksimum 1000 simvol ola bilər.")
        String description,
        @DecimalMin(value = "0.00", message = "Xidmət qiyməti mənfi ola bilməz.")
        @Digits(integer = 10, fraction = 2, message = "Xidmət qiyməti maksimum iki onluq rəqəm saxlaya bilər.")
        BigDecimal price,
        @NotNull(message = "Xidmətin aktivlik vəziyyəti mütləqdir.")
        Boolean active
) {
}
