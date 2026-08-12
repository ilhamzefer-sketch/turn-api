package az.turn.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRegistrationRequest(
        @NotBlank(message = "Ad mütləqdir.") @Size(min = 2, max = 80, message = "Ad 2-80 simvol olmalıdır.") @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Ad düzgün formatda deyil.") String firstName,
        @NotBlank(message = "Soyad mütləqdir.") @Size(min = 2, max = 80, message = "Soyad 2-80 simvol olmalıdır.") @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Soyad düzgün formatda deyil.") String lastName,
        @NotBlank(message = "Email mütləqdir.") @Email(message = "Email formatı düzgün deyil.") @Size(max = 254, message = "Email maksimum 254 simvol ola bilər.") String email,
        @NotBlank(message = "Şifrə mütləqdir.") @Size(min = 8, max = 72, message = "Şifrə 8-72 simvol olmalıdır.") @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "Şifrədə ən azı bir hərf və bir rəqəm olmalıdır.") String password
) {
}
