package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhoneChangeCreateRequestDto(
        @NotBlank @Size(max = 30) String requestedPhone,
        @NotBlank @Size(max = 1000) String reason
) {
}
