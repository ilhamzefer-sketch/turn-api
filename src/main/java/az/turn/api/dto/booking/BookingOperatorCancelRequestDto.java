package az.turn.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingOperatorCancelRequestDto(
        @NotBlank(message = "Ləğv səbəbi mütləqdir.")
        @Size(max = 500, message = "Ləğv səbəbi maksimum 500 simvol ola bilər.")
        String reason,
        @NotNull(message = "İştirakçı ilə əlaqə təsdiqi mütləqdir.")
        @AssertTrue(message = "Rezervasiyanı ləğv etməzdən əvvəl iştirakçı məlumatlandırılmalıdır.")
        Boolean participantInformed
) {
}
