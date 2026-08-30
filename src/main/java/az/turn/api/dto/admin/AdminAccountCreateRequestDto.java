package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminAccountCreateRequestDto(
        @NotBlank(message = "Admin istifadəçi adı mütləqdir.")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Admin istifadəçi adında yalnız hərf, rəqəm, nöqtə, alt xətt və tire ola bilər.")
        @Size(min = 3, max = 50, message = "Admin istifadəçi adı 3-50 simvol olmalıdır.")
        String username,
        @NotBlank(message = "Admin adı mütləqdir.")
        @Size(max = 120, message = "Admin adı maksimum 120 simvol ola bilər.")
        String displayName,
        @NotBlank(message = "Admin şifrəsi mütləqdir.")
        @Size(min = 8, max = 128, message = "Admin şifrəsi 8-128 simvol olmalıdır.")
        String password
) {
}
