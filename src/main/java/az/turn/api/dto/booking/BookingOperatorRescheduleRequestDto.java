package az.turn.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BookingOperatorRescheduleRequestDto(
        @NotNull(message = "Yeni rezervasiya saatı mütləqdir.")
        LocalDateTime startAt,
        @NotNull(message = "İştirakçı ilə əlaqə təsdiqi mütləqdir.")
        @AssertTrue(message = "Rezervasiyanı dəyişməzdən əvvəl iştirakçı məlumatlandırılmalıdır.")
        Boolean participantInformed
) {
}
