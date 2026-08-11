package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueueManagerLoginRequest(
        @NotBlank(message = "İstifadəçi adı mütləqdir.") @Size(min = 3, max = 100, message = "İstifadəçi adı 3-100 simvol olmalıdır.") String username,
        @NotBlank(message = "Şifrə mütləqdir.") @Size(max = 72, message = "Şifrə maksimum 72 simvol ola bilər.") String password
) {
}
