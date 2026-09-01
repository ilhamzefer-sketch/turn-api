package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminTopUpRejectRequestDto(
        @NotBlank(message = "Rədd səbəbi mütləqdir.")
        @Size(max = 1000, message = "Rədd səbəbi 1000 simvoldan uzun ola bilməz.")
        String reason
) {
}
