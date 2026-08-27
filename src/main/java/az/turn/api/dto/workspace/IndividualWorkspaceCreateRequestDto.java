package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IndividualWorkspaceCreateRequestDto(
        @NotBlank(message = "İş sahəsinin adı mütləqdir.")
        @Size(min = 2, max = 160, message = "İş sahəsinin adı 2-160 simvol olmalıdır.")
        String name,
        @Size(max = 60, message = "Saat qurşağı maksimum 60 simvol ola bilər.")
        String timezone
) {
}
