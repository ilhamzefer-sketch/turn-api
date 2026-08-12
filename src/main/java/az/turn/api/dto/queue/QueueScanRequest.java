package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QueueScanRequest(
        @NotBlank(message = "QR token mütləqdir.") @Size(max = 128, message = "QR token maksimum 128 simvol ola bilər.") @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "QR token düzgün formatda deyil.") String qrToken,
        @Positive(message = "Müştəri ID müsbət olmalıdır.") Long customerId,
        @Size(max = 150, message = "Növbə adı maksimum 150 simvol ola bilər.") String displayName,
        @Size(min = 2, max = 80, message = "Ad 2-80 simvol olmalıdır.") @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Ad düzgün formatda deyil.") String firstName,
        @Size(min = 2, max = 80, message = "Soyad 2-80 simvol olmalıdır.") @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Soyad düzgün formatda deyil.") String lastName
) {
}
