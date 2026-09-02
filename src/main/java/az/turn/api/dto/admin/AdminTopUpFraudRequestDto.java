package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminTopUpFraudRequestDto(
        @NotBlank(message = "Fırıldaq təsdiqi səbəbi mütləqdir.")
        @Size(max = 1000, message = "Fırıldaq təsdiqi səbəbi 1000 simvoldan uzun ola bilməz.")
        String reason
) {
}
