package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserLoginRequestDto(
        @NotBlank(message = "Telefon nömrəsi mütləqdir.")
        @Size(max = 30, message = "Telefon maksimum 30 simvol ola bilər.")
        String phone,
        @NotBlank(message = "Şifrə mütləqdir.")
        @Size(max = 128, message = "Şifrə maksimum 128 simvol ola bilər.")
        String password
) {
}
