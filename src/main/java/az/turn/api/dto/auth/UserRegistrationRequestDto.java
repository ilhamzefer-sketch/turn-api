package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequestDto(
        @NotBlank(message = "Ad mütləqdir.")
        @Size(min = 2, max = 80, message = "Ad 2-80 simvol olmalıdır.")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Ad düzgün formatda deyil.")
        String firstName,
        @NotBlank(message = "Soyad mütləqdir.")
        @Size(min = 2, max = 80, message = "Soyad 2-80 simvol olmalıdır.")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Soyad düzgün formatda deyil.")
        String lastName,
        @NotBlank(message = "Telefon nömrəsi mütləqdir.")
        @Size(max = 30, message = "Telefon maksimum 30 simvol ola bilər.")
        String phone,
        @NotBlank(message = "Şifrə mütləqdir.")
        @Size(min = 8, max = 128, message = "Şifrə 8-128 simvol olmalıdır.")
        String password
) {
}
