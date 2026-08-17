package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IndividualWorkspaceCreateRequestDto(
        @NotBlank(message = "Workspace adı mütləqdir.")
        @Size(max = 160, message = "Workspace adı maksimum 160 simvol ola bilər.")
        String name,
        @Size(max = 60, message = "Saat qurşağı maksimum 60 simvol ola bilər.")
        String timezone
) {
}
