package az.turn.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BranchUpsertRequestDto(
        @NotBlank(message = "Filial adı mütləqdir.")
        @Size(max = 160, message = "Filial adı maksimum 160 simvol ola bilər.")
        String name,
        @NotBlank(message = "Filial ünvanı mütləqdir.")
        @Size(max = 500, message = "Filial ünvanı maksimum 500 simvol ola bilər.")
        String address,
        @NotBlank(message = "Şəhər mütləqdir.")
        @Size(max = 120, message = "Şəhər maksimum 120 simvol ola bilər.")
        String city,
        @NotBlank(message = "Rayon mütləqdir.")
        @Size(max = 120, message = "Rayon maksimum 120 simvol ola bilər.")
        String district,
        @DecimalMin(value = "-90", message = "Enlik -90-dan kiçik ola bilməz.")
        @DecimalMax(value = "90", message = "Enlik 90-dan böyük ola bilməz.")
        BigDecimal latitude,
        @DecimalMin(value = "-180", message = "Uzunluq -180-dan kiçik ola bilməz.")
        @DecimalMax(value = "180", message = "Uzunluq 180-dan böyük ola bilməz.")
        BigDecimal longitude,
        String phone,
        @Size(max = 2000, message = "Qeyd maksimum 2000 simvol ola bilər.")
        String notes,
        @Size(max = 60, message = "Saat qurşağı maksimum 60 simvol ola bilər.")
        String timezone
) {
}
