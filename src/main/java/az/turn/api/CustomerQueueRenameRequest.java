package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CustomerQueueRenameRequest(
        @Positive(message = "Müştəri ID müsbət olmalıdır.") Long customerId,
        @NotBlank(message = "Növbə adı mütləqdir.") @Size(max = 150, message = "Növbə adı maksimum 150 simvol ola bilər.") String displayName
) {
}
