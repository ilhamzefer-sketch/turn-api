package az.turn.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email mütləqdir.") @Email(message = "Email formatı düzgün deyil.") @Size(max = 254, message = "Email maksimum 254 simvol ola bilər.") String email,
        @NotBlank(message = "Şifrə mütləqdir.") @Size(max = 72, message = "Şifrə maksimum 72 simvol ola bilər.") String password
) {
}
