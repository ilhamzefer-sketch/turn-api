package az.turn.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CustomerQueueJoinRequest(
        @Positive(message = "Müştəri ID müsbət olmalıdır.") Long customerId,
        @Positive(message = "Növbə ID müsbət olmalıdır.") Long queueId,
        @Size(max = 128, message = "QR token maksimum 128 simvol ola bilər.") @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "QR token düzgün formatda deyil.") String qrToken,
        @Size(max = 150, message = "Növbə adı maksimum 150 simvol ola bilər.") String displayName
) {
}
