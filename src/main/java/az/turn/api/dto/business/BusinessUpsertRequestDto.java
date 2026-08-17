package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessUpsertRequestDto(
        @NotBlank(message = "Biznes adı mütləqdir.")
        @Size(max = 160, message = "Biznes adı maksimum 160 simvol ola bilər.")
        String name,
        @Size(max = 200, message = "Hüquqi ad maksimum 200 simvol ola bilər.")
        String legalName,
        @Size(max = 2000, message = "Açıqlama maksimum 2000 simvol ola bilər.")
        String description,
        @Size(max = 40, message = "VÖEN maksimum 40 simvol ola bilər.")
        String taxId,
        @Size(max = 1000, message = "Logo ünvanı maksimum 1000 simvol ola bilər.")
        String logoUrl,
        @NotBlank(message = "Biznes telefonu mütləqdir.")
        String phone,
        @Size(max = 60, message = "Saat qurşağı maksimum 60 simvol ola bilər.")
        String timezone
) {
}
