package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record BookingManualCreateRequestDto(
        @NotBlank(message = "İştirakçının adı mütləqdir.")
        @Size(max = 160, message = "İştirakçının adı maksimum 160 simvol ola bilər.")
        String displayName,
        @NotBlank(message = "Telefon nömrəsi mütləqdir.")
        String phone,
        @NotNull(message = "Rezervasiya saatı mütləqdir.")
        LocalDateTime startAt,
        @Positive(message = "Xidmət identifikatoru müsbət olmalıdır.")
        Long serviceId,
        @NotNull(message = "Manual rezervasiya mənbəyi mütləqdir.")
        LiveQueueEntrySource source,
        @Size(max = 1000, message = "Daxili qeyd maksimum 1000 simvol ola bilər.")
        String internalNote
) {
}
