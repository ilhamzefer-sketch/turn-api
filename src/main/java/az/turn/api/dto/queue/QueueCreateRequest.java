package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QueueCreateRequest(
        long registrationId,
        @NotBlank(message = "Ünvan mütləqdir.") @Size(max = 300, message = "Ünvan maksimum 300 simvol ola bilər.") String address,
        @NotBlank(message = "İşin adı mütləqdir.") @Size(max = 150, message = "İşin adı maksimum 150 simvol ola bilər.") String serviceName,
        @NotEmpty(message = "Ən azı bir kateqoriya əlavə edin.") @Size(max = 50, message = "Maksimum 50 kateqoriya ola bilər.") List<@NotBlank(message = "Kateqoriya boş ola bilməz.") @Size(max = 100, message = "Kateqoriya maksimum 100 simvol ola bilər.") String> categories,
        @Pattern(regexp = "(?i)DAILY|CUSTOM|MANUAL", message = "Reset növü düzgün deyil.") String resetMode,
        @Pattern(regexp = "^$|\\d{4}-\\d{2}-\\d{2}", message = "Tarix YYYY-MM-DD formatında olmalıdır.") String resetAt,
        @Size(max = 100, message = "İdarəçi adı maksimum 100 simvol ola bilər.") @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "İdarəçi adı düzgün formatda deyil.") String managerUsername,
        @Size(max = 72, message = "İdarəçi şifrəsi maksimum 72 simvol ola bilər.") String managerPassword
) {
}
