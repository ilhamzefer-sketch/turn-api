package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank(message = "İstifadəçi adı mütləqdir.") @Size(max = 100, message = "İstifadəçi adı maksimum 100 simvol ola bilər.") String username,
        @NotBlank(message = "Şifrə mütləqdir.") @Size(max = 72, message = "Şifrə maksimum 72 simvol ola bilər.") String password
) {
}
