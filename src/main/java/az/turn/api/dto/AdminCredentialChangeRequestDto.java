package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCredentialChangeRequestDto(
        @NotBlank(message = "Cari şifrə mütləqdir.")
        @Size(max = 128, message = "Cari şifrə maksimum 128 simvol ola bilər.")
        String currentPassword,
        @NotBlank(message = "Yeni admin istifadəçi adı mütləqdir.")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Admin istifadəçi adında yalnız hərf, rəqəm, nöqtə, alt xətt və tire ola bilər.")
        @Size(min = 3, max = 50, message = "Admin istifadəçi adı 3-50 simvol olmalıdır.")
        String newUsername,
        @NotBlank(message = "Yeni admin şifrəsi mütləqdir.")
        @Size(min = 8, max = 128, message = "Admin şifrəsi 8-128 simvol olmalıdır.")
        String newPassword
) {
}
